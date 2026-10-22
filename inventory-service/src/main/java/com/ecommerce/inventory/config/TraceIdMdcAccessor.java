package com.ecommerce.inventory.config;

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

/**
 * Bridges the MDC entry used by the Logback pattern with Reactor/Publisher
 * contexts so the trace id is visible in logs across thread switches when
 * automatic context propagation is enabled.
 */
public class TraceIdMdcAccessor implements ThreadLocalAccessor<String> {

    private static final String MDC_KEY = "traceId";

    @Override
    public Object key() {
        return TraceIdWebFilter.CONTEXT_KEY;
    }

    @Override
    public String getValue() {
        return MDC.get(MDC_KEY);
    }

    @Override
    public void setValue(String value) {
        MDC.put(MDC_KEY, value);
    }

    @Override
    public void setValue() {
        MDC.remove(MDC_KEY);
    }
}
