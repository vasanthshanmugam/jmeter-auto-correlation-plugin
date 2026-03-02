package io.github.vasanthshanmugam.jmeter.plugins.correlation;

import org.apache.jmeter.testelement.AbstractTestElement;

import java.io.Serializable;

/**
 * Inert test element that serves as a placeholder in the JMeter test plan tree.
 * The actual correlation logic is triggered by the GUI's "Click to Correlate" button,
 * not during test execution. This element does not execute during a test run.
 *
 * Class name kept as CorrelationPostProcessor for backwards compatibility with
 * saved .jmx files, even though it no longer implements PostProcessor.
 */
public class CorrelationPostProcessor extends AbstractTestElement implements Serializable {

    private static final long serialVersionUID = 2L;

    public CorrelationPostProcessor() {
        super();
    }
}
