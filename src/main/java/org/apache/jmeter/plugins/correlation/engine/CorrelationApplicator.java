package org.apache.jmeter.plugins.correlation.engine;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.extractor.RegexExtractor;
import org.apache.jmeter.extractor.json.jsonpath.JSONPostProcessor;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.plugins.correlation.model.CorrelationCandidate;
import org.apache.jmeter.plugins.correlation.model.ExtractorType;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.testelement.TestElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Applies selected correlations to the live JMeter test plan tree:
 * 1. Adds RegexExtractor or JSONPostProcessor as child of source sampler
 * 2. Replaces hardcoded values in target samplers with ${variableName}
 */
public class CorrelationApplicator {

    private static final Logger log = LoggerFactory.getLogger(CorrelationApplicator.class);

    /**
     * Apply the selected correlation candidates to the test plan.
     * Must be called on the Swing EDT.
     *
     * @param candidates the user-selected candidates to apply
     */
    public void apply(List<CorrelationCandidate> candidates) {
        GuiPackage guiPackage = GuiPackage.getInstance();
        JMeterTreeModel model = guiPackage.getTreeModel();

        // Collect all HTTP sampler nodes in execution order
        List<JMeterTreeNode> samplerNodes = collectSamplerNodes(model);

        int extractorsAdded = 0;
        int valuesReplaced = 0;

        for (CorrelationCandidate candidate : candidates) {
            // A. Add extractor to the source sampler
            int sourceIdx = candidate.getSourceSamplerIndex();
            if (sourceIdx >= 0 && sourceIdx < samplerNodes.size()) {
                JMeterTreeNode sourceNode = samplerNodes.get(sourceIdx);
                addExtractor(sourceNode, candidate, model);
                extractorsAdded++;
            }

            // B. Replace hardcoded values in target samplers
            for (int targetIdx : candidate.getTargetSamplerIndices()) {
                if (targetIdx >= 0 && targetIdx < samplerNodes.size()) {
                    JMeterTreeNode targetNode = samplerNodes.get(targetIdx);
                    boolean replaced = replaceHardcodedValues(targetNode, candidate);
                    if (replaced) valuesReplaced++;
                }
            }
        }

        // Refresh the GUI tree
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        model.nodeStructureChanged(root);

        log.info("Applied {} extractors and replaced {} hardcoded values.",
                extractorsAdded, valuesReplaced);
    }

    /**
     * Collect all JMeterTreeNodes containing HTTPSamplerBase in depth-first order.
     */
    private List<JMeterTreeNode> collectSamplerNodes(JMeterTreeModel model) {
        List<JMeterTreeNode> nodes = new ArrayList<>();
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        Enumeration<TreeNode> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            JMeterTreeNode node = (JMeterTreeNode) enumeration.nextElement();
            if (node.getTestElement() instanceof AbstractSampler) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * Add a RegexExtractor or JSONPostProcessor as a child of the sampler node.
     */
    private void addExtractor(JMeterTreeNode samplerNode,
                              CorrelationCandidate candidate,
                              JMeterTreeModel model) {
        try {
            TestElement extractor;
            if (candidate.getExtractorType() == ExtractorType.JSON) {
                extractor = createJsonExtractor(candidate);
            } else {
                extractor = createRegexExtractor(candidate);
            }

            JMeterTreeNode extractorNode = new JMeterTreeNode(extractor, model);
            model.insertNodeInto(extractorNode, samplerNode, samplerNode.getChildCount());

            log.debug("Added {} extractor '{}' under sampler '{}'",
                    candidate.getExtractorType(),
                    extractor.getName(),
                    samplerNode.getName());
        } catch (Exception e) {
            log.error("Failed to add extractor for {}: {}",
                    candidate.getParameterName(), e.getMessage());
        }
    }

    /**
     * Create a RegexExtractor test element.
     */
    private RegexExtractor createRegexExtractor(CorrelationCandidate candidate) {
        RegexExtractor extractor = new RegexExtractor();
        extractor.setName("Extract_" + candidate.getVariableName());
        extractor.setRefName(candidate.getVariableName());
        extractor.setRegex(candidate.getExtractionPattern());
        extractor.setTemplate("$1$");
        extractor.setMatchNumber(1);
        extractor.setDefaultValue("NOT_FOUND");
        extractor.setProperty(TestElement.TEST_CLASS, RegexExtractor.class.getName());
        extractor.setProperty(TestElement.GUI_CLASS,
                "org.apache.jmeter.extractor.gui.RegexExtractorGui");
        return extractor;
    }

    /**
     * Create a JSONPostProcessor test element.
     */
    private JSONPostProcessor createJsonExtractor(CorrelationCandidate candidate) {
        JSONPostProcessor extractor = new JSONPostProcessor();
        extractor.setName("Extract_" + candidate.getVariableName());
        extractor.setRefNames(candidate.getVariableName());
        extractor.setJsonPathExpressions(candidate.getExtractionPattern());
        extractor.setMatchNumbers("1");
        extractor.setDefaultValues("NOT_FOUND");
        extractor.setProperty(TestElement.TEST_CLASS, JSONPostProcessor.class.getName());
        extractor.setProperty(TestElement.GUI_CLASS,
                "org.apache.jmeter.extractor.json.jsonpath.gui.JSONPostProcessorGui");
        return extractor;
    }

    /**
     * Replace hardcoded values in a target sampler with ${variableName}.
     *
     * @return true if any replacement was made
     */
    private boolean replaceHardcodedValues(JMeterTreeNode targetNode,
                                           CorrelationCandidate candidate) {
        TestElement te = targetNode.getTestElement();
        if (!(te instanceof AbstractSampler)) return false;

        String variableRef = "${" + candidate.getVariableName() + "}";
        String paramName = candidate.getParameterName();
        String sampleValue = candidate.getSampleValue();
        boolean replaced = false;

        if (te instanceof HTTPSamplerBase) {
            HTTPSamplerBase sampler = (HTTPSamplerBase) te;

            // Replace in request arguments
            Arguments args = sampler.getArguments();
            if (args != null) {
                for (int i = 0; i < args.getArgumentCount(); i++) {
                    org.apache.jmeter.config.Argument arg = args.getArgument(i);

                    // Match by parameter name
                    if (arg.getName() != null && arg.getName().equalsIgnoreCase(paramName)) {
                        arg.setValue(variableRef);
                        replaced = true;
                    }
                    // Match by value content
                    else if (sampleValue != null && sampleValue.length() > 8
                            && arg.getValue() != null
                            && arg.getValue().contains(sampleValue)) {
                        arg.setValue(arg.getValue().replace(sampleValue, variableRef));
                        replaced = true;
                    }
                }
            }

            // Replace in URL path
            String path = sampler.getPath();
            if (path != null && sampleValue != null && sampleValue.length() > 8
                    && path.contains(sampleValue)) {
                sampler.setPath(path.replace(sampleValue, variableRef));
                replaced = true;
            }
        } else {
            // Non-HTTP sampler (e.g., DummySampler): scan ALL string properties and replace.
            // The exact property key varies across plugin versions (e.g., "requestData" vs
            // "DummySampler.requestData"), so scanning all avoids guessing the key.
            AbstractSampler sampler = (AbstractSampler) te;
            if (sampleValue != null && sampleValue.length() > 8) {
                org.apache.jmeter.testelement.property.PropertyIterator propIter =
                        sampler.propertyIterator();
                while (propIter.hasNext()) {
                    org.apache.jmeter.testelement.property.JMeterProperty prop = propIter.next();
                    String propVal = prop.getStringValue();
                    if (propVal != null && propVal.contains(sampleValue)) {
                        sampler.setProperty(prop.getName(),
                                propVal.replace(sampleValue, variableRef));
                        replaced = true;
                    }
                }
            }
        }

        // Replace in child HeaderManagers (e.g., Authorization: Bearer <token>).
        // Applies to both HTTP and non-HTTP samplers — the value may live in a
        // HeaderManager placed under the sampler node regardless of sampler type.
        if (sampleValue != null && sampleValue.length() > 8) {
            for (int c = 0; c < targetNode.getChildCount(); c++) {
                JMeterTreeNode child = (JMeterTreeNode) targetNode.getChildAt(c);
                TestElement childTe = child.getTestElement();
                if (childTe instanceof HeaderManager) {
                    HeaderManager hm = (HeaderManager) childTe;
                    for (int h = 0; h < hm.size(); h++) {
                        Header header = hm.get(h);
                        if (header.getValue() != null && header.getValue().contains(sampleValue)) {
                            header.setValue(header.getValue().replace(sampleValue, variableRef));
                            replaced = true;
                            log.debug("Replaced value in HeaderManager under '{}'", te.getName());
                        }
                    }
                }
            }
        }

        if (replaced) {
            log.debug("Replaced hardcoded value in sampler '{}' with {}",
                    te.getName(), variableRef);
        }
        return replaced;
    }
}
