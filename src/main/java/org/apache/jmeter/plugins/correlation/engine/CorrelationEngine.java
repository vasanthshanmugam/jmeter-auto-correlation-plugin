package org.apache.jmeter.plugins.correlation.engine;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.JMeter;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.plugins.correlation.detection.CorrelationDetector;
import org.apache.jmeter.plugins.correlation.model.CorrelationCandidate;
import org.apache.jmeter.plugins.correlation.model.CorrelationType;
import org.apache.jmeter.plugins.correlation.model.ExtractorType;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.ListedHashTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.tree.TreeNode;

/**
 * Core orchestrator for the correlation workflow:
 * 1. Collect all HTTP samplers from the test plan tree
 * 2. Re-run the test plan once to capture live responses
 * 3. Detect dynamic values in each response
 * 4. Cross-reference detected values with subsequent request parameters
 * 5. Generate extraction patterns for selected candidates
 */
public class CorrelationEngine {

    private static final Logger log = LoggerFactory.getLogger(CorrelationEngine.class);

    private final CorrelationDetector detector = new CorrelationDetector();

    // Populated by collectSamplers(); maps each sampler to the header values
    // that are in scope for it (from its own child HeaderManagers and sibling HeaderManagers).
    // Used by crossReferenceWithRequests() to detect tokens passed via HTTP headers.
    private final Map<AbstractSampler, List<String>> samplerHeaders = new HashMap<>();

    // Diagnostic counters — set during execute() so the GUI can report them
    private int lastSamplersFound = 0;
    private int lastResultsCaptured = 0;
    private int lastRawCandidatesFound = 0;
    private int lastReusedCandidatesFound = 0;

    public int getLastSamplersFound()       { return lastSamplersFound; }
    public int getLastResultsCaptured()     { return lastResultsCaptured; }
    public int getLastRawCandidatesFound()  { return lastRawCandidatesFound; }
    public int getLastReusedCandidatesFound() { return lastReusedCandidatesFound; }

    /**
     * Execute the full correlation workflow.
     * Must be called from a background thread (not the EDT).
     *
     * @return list of correlation candidates that are reused in subsequent requests
     * @throws Exception if test replay fails
     */
    public List<CorrelationCandidate> execute() throws Exception {
        // Step A: Collect all HTTP samplers in execution order from the GUI tree
        List<AbstractSampler> samplers = collectSamplers();
        lastSamplersFound = samplers.size();
        if (samplers.isEmpty()) {
            log.warn("No samplers found in the test plan.");
            return new ArrayList<>();
        }
        log.warn("Found {} samplers in the test plan.", samplers.size());

        // Step B: Re-run the test plan and capture responses
        List<SampleResult> results = replayTestPlan();
        lastResultsCaptured = results.size();
        log.warn("Captured {} sample results from test replay.", results.size());

        // Step C: Detect dynamic values in each response
        List<CorrelationCandidate> allCandidates = detectAllCandidates(samplers, results);
        lastRawCandidatesFound = allCandidates.size();
        log.warn("Detected {} raw correlation candidates.", allCandidates.size());

        // Step D: Cross-reference with subsequent requests
        crossReferenceWithRequests(allCandidates, samplers);

        // Filter: keep only candidates that are reused in subsequent requests
        List<CorrelationCandidate> reusedCandidates = new ArrayList<>();
        for (CorrelationCandidate c : allCandidates) {
            if (c.getUsageCount() > 0) {
                reusedCandidates.add(c);
            }
        }
        lastReusedCandidatesFound = reusedCandidates.size();

        // Step E: Generate extraction patterns
        for (CorrelationCandidate c : reusedCandidates) {
            generateExtractionPattern(c);
        }

        log.warn("Found {} correlation candidates that are reused in subsequent requests.",
                reusedCandidates.size());
        return reusedCandidates;
    }

    /**
     * Traverse the JMeter GUI tree and collect all HTTPSamplerBase elements
     * in depth-first (execution) order. Also populates {@link #samplerHeaders}
     * with the header values that are in scope for each sampler.
     */
    List<AbstractSampler> collectSamplers() {
        List<AbstractSampler> samplers = new ArrayList<>();
        samplerHeaders.clear();
        JMeterTreeModel model = GuiPackage.getInstance().getTreeModel();
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();

        Enumeration<TreeNode> nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            JMeterTreeNode node = (JMeterTreeNode) nodes.nextElement();
            TestElement te = node.getTestElement();
            if (te instanceof AbstractSampler) {
                samplers.add((AbstractSampler) te);
                samplerHeaders.put((AbstractSampler) te, collectHeaderValues(node));
            }
        }
        return samplers;
    }

    /**
     * Collect all header values in scope for a sampler node:
     * - HeaderManagers that are direct children of the sampler (most common — added under the sampler)
     * - HeaderManagers that are siblings of the sampler (added at ThreadGroup/controller level)
     */
    private List<String> collectHeaderValues(JMeterTreeNode samplerNode) {
        List<String> values = new ArrayList<>();

        // Direct children (e.g., HeaderManager placed under this specific sampler)
        for (int i = 0; i < samplerNode.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) samplerNode.getChildAt(i);
            addHeaderManagerValues(child.getTestElement(), values);
        }

        // Siblings at the same level (e.g., HeaderManager at ThreadGroup level)
        TreeNode parent = samplerNode.getParent();
        if (parent instanceof JMeterTreeNode) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                JMeterTreeNode sibling = (JMeterTreeNode) parent.getChildAt(i);
                if (sibling == samplerNode) continue;
                addHeaderManagerValues(sibling.getTestElement(), values);
            }
        }

        return values;
    }

    /** Extract all non-empty header values from a HeaderManager element. */
    private void addHeaderManagerValues(TestElement te, List<String> values) {
        if (!(te instanceof HeaderManager)) return;
        HeaderManager hm = (HeaderManager) te;
        for (int j = 0; j < hm.size(); j++) {
            Header h = hm.get(j);
            if (h != null && h.getValue() != null && !h.getValue().isEmpty()) {
                values.add(h.getValue());
            }
        }
    }

    /**
     * Re-run the test plan with 1 thread, 1 iteration and capture all responses.
     */
    List<SampleResult> replayTestPlan() throws Exception {
        GuiPackage guiPackage = GuiPackage.getInstance();

        // Build the execution tree from the GUI model.
        // getTestPlan() returns a ListedHashTree whose keys are JMeterTreeNode wrappers,
        // NOT TestElement objects. StandardJMeterEngine.configure() uses SearchByClass<TestPlan>
        // which checks instanceof TestPlan — JMeterTreeNode fails that check → IllegalArgumentException.
        HashTree testPlanTree = guiPackage.getTreeModel().getTestPlan();

        // Step 1: shallow-clone tree structure so we don't touch the live GUI tree.
        ListedHashTree clonedTree = (ListedHashTree) testPlanTree.clone();

        // Step 2: JMeter.convertSubTree(tree, true):
        //   - pConvertSubTree()  → replaces every JMeterTreeNode key with node.getTestElement() in-place
        //   - TreeCloner(false)  → deep-clones the now-proper TestElement keys
        // This matches exactly what JMeter's own RunAction does before engine.configure().
        HashTree execTree = JMeter.convertSubTree(clonedTree, true);

        // Create our response collector and a latch to detect test completion.
        // engine.run() may return before test threads finish (non-blocking in some JMeter builds),
        // so we use a TestStateListener + CountDownLatch to reliably wait for the end.
        CountDownLatch testDone = new CountDownLatch(1);
        ResponseCollector.prepare();           // clear static results list
        TestEndNotifier.prepare(testDone);     // register static latch
        ResponseCollector collector = new ResponseCollector();
        TestEndNotifier endNotifier = new TestEndNotifier();

        // Add the collector and end-notifier at the TEST PLAN level — the same scope used by
        // JMeter's built-in "View Results Tree". This guarantees they are in scope for every
        // sampler in every thread group, regardless of how deep the samplers are nested.
        //
        // NOTE: JMeter clones each test element for every virtual thread via
        // AbstractTestElement.clone() → getClass().newInstance(). Because of this, instance
        // fields on cloned objects won't be visible to the original reference we hold here.
        // Both inner classes therefore use static fields to share state across all instances.
        for (Object key : execTree.list()) {
            HashTree testPlanSubTree = execTree.getTree(key);
            if (testPlanSubTree == null) continue;

            testPlanSubTree.add(collector);
            testPlanSubTree.add(endNotifier);

            // Force 1 thread/1 loop on every ThreadGroup
            for (Object subKey : testPlanSubTree.list()) {
                if (subKey instanceof ThreadGroup) {
                    ThreadGroup tg = (ThreadGroup) subKey;
                    tg.setNumThreads(1);

                    if (tg.getSamplerController() instanceof LoopController) {
                        LoopController lc = (LoopController) tg.getSamplerController();
                        lc.setLoops(1);
                        lc.setContinueForever(false);
                    }
                }
            }
            break; // only one TestPlan expected
        }

        // Run the test
        log.warn("CorrelationEngine: starting StandardJMeterEngine...");
        StandardJMeterEngine engine = new StandardJMeterEngine();
        engine.configure(execTree);
        engine.run(); // may or may not block until test threads finish
        log.warn("CorrelationEngine: engine.run() returned, waiting for testEnded signal...");

        // Wait up to 5 minutes for the test to finish (handles non-blocking run())
        boolean finished = testDone.await(300, TimeUnit.SECONDS);
        List<SampleResult> results = ResponseCollector.getSharedResults();
        log.warn("CorrelationEngine: test finished={}, results captured={}",
                finished, results.size());

        return results;
    }

    /**
     * Detect dynamic values in each response using the CorrelationDetector.
     */
    List<CorrelationCandidate> detectAllCandidates(
            List<AbstractSampler> samplers, List<SampleResult> results) {

        List<CorrelationCandidate> allCandidates = new ArrayList<>();

        // Match results to samplers by label
        for (int resultIdx = 0; resultIdx < results.size(); resultIdx++) {
            SampleResult result = results.get(resultIdx);
            String responseBody = result.getResponseDataAsString();
            if (responseBody == null || responseBody.isEmpty()) {
                continue;
            }

            // Find matching sampler by label
            int samplerIdx = findSamplerIndexByLabel(samplers, result.getSampleLabel());
            if (samplerIdx < 0) {
                // Fallback: use result index if within range
                if (resultIdx < samplers.size()) {
                    samplerIdx = resultIdx;
                } else {
                    continue;
                }
            }

            AbstractSampler sampler = samplers.get(samplerIdx);
            List<CorrelationCandidate> candidates =
                    detector.detectFromResponse(responseBody, sampler);

            for (CorrelationCandidate c : candidates) {
                c.setSourceSamplerIndex(samplerIdx);
                c.setSourceSamplerName(sampler.getName());
                log.warn("Detected candidate: paramName='{}' value='{}' type={} from sampler[{}]='{}'",
                        c.getParameterName(), c.getSampleValue(), c.getCorrelationType(),
                        samplerIdx, sampler.getName());
                allCandidates.add(c);
            }
        }
        return allCandidates;
    }

    /**
     * Find sampler index by matching its name to the sample label.
     */
    private int findSamplerIndexByLabel(List<AbstractSampler> samplers, String label) {
        if (label == null) return -1;
        for (int i = 0; i < samplers.size(); i++) {
            if (label.equals(samplers.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Cross-reference detected values with parameters in subsequent requests.
     * A candidate is considered "reused" if a later sampler has a parameter
     * matching by name (case-insensitive) or by value.
     */
    void crossReferenceWithRequests(
            List<CorrelationCandidate> candidates, List<AbstractSampler> samplers) {

        for (CorrelationCandidate candidate : candidates) {
            int sourceIdx = candidate.getSourceSamplerIndex();
            String paramName = candidate.getParameterName();
            String sampleValue = candidate.getSampleValue();

            for (int i = sourceIdx + 1; i < samplers.size(); i++) {
                AbstractSampler sampler = samplers.get(i);
                boolean found = false;

                if (sampler instanceof HTTPSamplerBase) {
                    HTTPSamplerBase httpSampler = (HTTPSamplerBase) sampler;

                    // Check request arguments
                    Arguments args = httpSampler.getArguments();
                    if (args != null) {
                        for (int j = 0; j < args.getArgumentCount(); j++) {
                            org.apache.jmeter.config.Argument arg = args.getArgument(j);
                            // Match by parameter name (case-insensitive)
                            if (arg.getName() != null && arg.getName().equalsIgnoreCase(paramName)) {
                                found = true;
                                break;
                            }
                            // Match by value (for values long enough to avoid false positives)
                            if (sampleValue != null && sampleValue.length() > 8
                                    && arg.getValue() != null
                                    && arg.getValue().contains(sampleValue)) {
                                found = true;
                                break;
                            }
                        }
                    }

                    // Check URL path
                    if (!found && sampleValue != null && sampleValue.length() > 8) {
                        String path = httpSampler.getPath();
                        if (path != null && path.contains(sampleValue)) {
                            found = true;
                        }
                    }
                } else {
                    // Non-HTTP samplers (e.g., DummySampler): scan ALL string properties.
                    // DummySampler may store request body under "requestData",
                    // "DummySampler.requestData", or similar — scanning all avoids
                    // guessing the exact property key.
                    if (sampleValue != null && sampleValue.length() > 8) {
                        org.apache.jmeter.testelement.property.PropertyIterator propIter =
                                sampler.propertyIterator();
                        while (propIter.hasNext() && !found) {
                            String propVal = propIter.next().getStringValue();
                            if (propVal == null) continue;
                            if (propVal.contains(sampleValue)) {
                                found = true;
                            } else if (paramName != null
                                    && propVal.toLowerCase().contains(paramName.toLowerCase())) {
                                found = true;
                            }
                        }
                        log.warn(
                            "CrossRef non-HTTP sampler '{}': paramName='{}' sampleValue='{}' found={}",
                            sampler.getName(), paramName, sampleValue, found);
                    }
                }

                // Check HTTP Header Manager values (e.g., Authorization: Bearer <token>)
                if (!found && sampleValue != null && sampleValue.length() > 8) {
                    List<String> headers = samplerHeaders.get(sampler);
                    if (headers != null) {
                        for (String headerVal : headers) {
                            if (headerVal != null && headerVal.contains(sampleValue)) {
                                found = true;
                                break;
                            }
                        }
                    }
                }

                if (found) {
                    candidate.addTargetRequest(sampler);
                    candidate.addTargetSamplerIndex(i);
                }
            }
        }
    }

    /**
     * Generate the appropriate extraction pattern based on extractor type.
     */
    void generateExtractionPattern(CorrelationCandidate candidate) {
        String paramName = candidate.getParameterName();
        if (candidate.getExtractorType() == ExtractorType.JSON) {
            // JSONPath expression
            candidate.setExtractionPattern("$." + paramName);
        } else if (candidate.getCorrelationType() == CorrelationType.SESSION_ID) {
            // Cookie / Set-Cookie format: JSESSIONID=VALUE; Path=/
            // Simple and correct: (?i)\bJSESSIONID=([A-Za-z0-9_%-]+)
            candidate.setExtractionPattern(
                    "(?i)\\b" + escapeRegex(paramName) + "=([A-Za-z0-9_%-]+)"
            );
        } else if (candidate.getCorrelationType() == CorrelationType.AUTH_TOKEN) {
            // OAuth / Bearer token in JSON responses.
            // Handles both plain JSON  ("access_token": "VALUE")
            // and backslash-escaped   (\"access_token\": \"VALUE\") — the latter
            // appears when servers like postman-echo echo the raw request body
            // inside a JSON string field (e.g., "data": "{...}").
            String escaped = escapeRegex(paramName);
            candidate.setExtractionPattern(
                    "\\\\?\"?" + escaped + "\\\\?\"?\\s*:\\s*\\\\?\"([^\"\\\\]+)"
            );
        } else if (candidate.getCorrelationType() == CorrelationType.HIDDEN_FIELD) {
            // HTML hidden input: lookahead asserts name="X" is present anywhere in the tag,
            // then captures value="..." regardless of attribute order.
            // Matches: <input type="hidden" name="_sourcePage" value="lTgbZD..."/>
            String escaped = escapeRegex(paramName);
            candidate.setExtractionPattern(
                    "<input(?=[^>]*\\bname=[\"']" + escaped + "[\"'])[^>]*\\bvalue=[\"']([^\"']+)[\"']"
            );
        } else {
            // Generic regex for JSON/other contexts
            // Handles: "paramName": "value", paramName="value"
            // The value group stops at quote, whitespace, comma, }, < or ;
            candidate.setExtractionPattern(
                    "\"?" + escapeRegex(paramName) + "\"?\\s*(?:value\\s*=|content\\s*=|[:=])\\s*\"?([^\"\\s,}<;]+)\"?"
            );
        }
    }

    /**
     * Escape special regex characters in a string.
     */
    private String escapeRegex(String input) {
        return input.replaceAll("([\\\\\\[\\](){}.*+?^$|])", "\\\\$1");
    }

    /**
     * Collects SampleResult objects during test replay.
     *
     * JMeter clones every test element for each virtual thread via
     * AbstractTestElement.clone() → getClass().newInstance(). A clone would have
     * its own empty instance list, so results written to the clone would be lost.
     * Using a static synchronized list ensures all instances (original + clones)
     * share the same storage.
     */
    public static class ResponseCollector extends AbstractTestElement
            implements SampleListener {

        private static final long serialVersionUID = 1L;

        // Shared across the original instance and any JMeter-created clones.
        private static volatile List<SampleResult> sharedResults = null;

        /** Must be called before each test run to reset the result set. */
        public static void prepare() {
            sharedResults = Collections.synchronizedList(new ArrayList<>());
        }

        /** Returns a snapshot of all collected results. */
        public static List<SampleResult> getSharedResults() {
            List<SampleResult> r = sharedResults;
            return (r != null) ? new ArrayList<>(r) : new ArrayList<>();
        }

        @Override
        public void sampleOccurred(SampleEvent event) {
            SampleResult result = event.getResult();
            List<SampleResult> r = sharedResults;
            if (result != null && r != null) {
                r.add(result);
            }
        }

        @Override public void sampleStarted(SampleEvent e) { /* no-op */ }
        @Override public void sampleStopped(SampleEvent e) { /* no-op */ }
    }

    /**
     * Signals test completion via a static CountDownLatch.
     *
     * Like ResponseCollector, this class may be cloned by JMeter.
     * The latch is held in a static field so any instance (original or clone)
     * that receives testEnded() counts down the correct latch.
     * A no-arg constructor is required for JMeter's reflection-based cloning.
     */
    public static class TestEndNotifier extends AbstractTestElement
            implements TestStateListener {

        private static final long serialVersionUID = 1L;
        private static final Logger log =
                LoggerFactory.getLogger(TestEndNotifier.class);

        // Shared across original and any JMeter-created clones.
        private static volatile CountDownLatch activeLatch = null;

        /** Required by JMeter's reflection-based clone (getClass().newInstance()). */
        public TestEndNotifier() {}

        /** Must be called before each test run to register the latch. */
        public static void prepare(CountDownLatch latch) {
            activeLatch = latch;
        }

        @Override public void testStarted() {
            log.warn("TestEndNotifier: testStarted()");
        }
        @Override public void testStarted(String host) { testStarted(); }

        @Override
        public void testEnded() {
            log.warn("TestEndNotifier: testEnded()");
            CountDownLatch latch = activeLatch;
            if (latch != null) latch.countDown();
        }
        @Override public void testEnded(String host) { testEnded(); }
    }
}
