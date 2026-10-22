package com.ecommerce.inventory.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;

/**
 * Logs service layer entry/exit and duration. Trace ids travel in the Reactor
 * context (put there by TraceIdWebFilter) and are written through MDC.
 */
@Aspect
@Component
@Slf4j
public class ServiceLoggingAspect {

    @Pointcut("within(com.ecommerce.inventory.service..*)")
    public void serviceLayer() {
    }

    @Around("serviceLayer()")
    public Object aroundService(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        if (result instanceof Mono<?> mono) {
            return Mono.deferContextual(ctx -> logAround(pjp, traceId(ctx), mono));
        }
        if (result instanceof Flux<?> flux) {
            return Flux.deferContextual(ctx -> logAround(pjp, traceId(ctx), flux));
        }
        return result;
    }

    private <T> Mono<T> logAround(ProceedingJoinPoint pjp, String traceId, Mono<T> mono) {
        long start = System.currentTimeMillis();
        log.info("[{}] ENTER {} args={}", traceId, pjp.getSignature().toShortString(), Arrays.toString(pjp.getArgs()));
        return mono.doOnSuccess(v -> log.info("[{}] EXIT {} completedIn={}ms", traceId,
                        pjp.getSignature().toShortString(), System.currentTimeMillis() - start))
                .doOnError(t -> log.error("[{}] ERROR {} failedIn={}ms error={}", traceId,
                        pjp.getSignature().toShortString(), System.currentTimeMillis() - start, t.getMessage()));
    }

    private <T> Flux<T> logAround(ProceedingJoinPoint pjp, String traceId, Flux<T> flux) {
        long start = System.currentTimeMillis();
        log.info("[{}] ENTER {} args={}", traceId, pjp.getSignature().toShortString(), Arrays.toString(pjp.getArgs()));
        return flux.doOnComplete(() -> log.info("[{}] EXIT {} completedIn={}ms", traceId,
                        pjp.getSignature().toShortString(), System.currentTimeMillis() - start))
                .doOnError(t -> log.error("[{}] ERROR {} failedIn={}ms error={}", traceId,
                        pjp.getSignature().toShortString(), System.currentTimeMillis() - start, t.getMessage()));
    }

    private String traceId(reactor.util.context.ContextView ctx) {
        return ctx.getOrDefault(com.ecommerce.inventory.config.TraceIdWebFilter.CONTEXT_KEY, "NO-TRACE");
    }
}
