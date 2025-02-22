/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_FLAGS;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.logback.mdc.v1_0.internal.UnionMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
    implements AppenderAttachable<ILoggingEvent> {
  private volatile boolean addBaggage;

  private final AppenderAttachableImpl<ILoggingEvent> aai = new AppenderAttachableImpl<>();

  /**
   * When set to true this will enable addition of all baggage entries to MDC. This can be done by
   * adding the following to the logback.xml config for this appender. {@code
   * <addBaggage>true</addBaggage>}
   *
   * @param addBaggage True if baggage should be added to MDC
   */
  public void setAddBaggage(boolean addBaggage) {
    this.addBaggage = addBaggage;
  }

  public ILoggingEvent wrapEvent(ILoggingEvent event) {
    Map<String, String> eventContext = event.getMDCPropertyMap();
    if (eventContext != null && eventContext.containsKey(TRACE_ID)) {
      // Assume already instrumented event if traceId is present.
      return event;
    }

    Map<String, String> contextData = new HashMap<>();
    Context context = Context.current();
    Span currentSpan = Span.fromContext(context);

    if (currentSpan.getSpanContext().isValid()) {
      SpanContext spanContext = currentSpan.getSpanContext();
      contextData.put(TRACE_ID, spanContext.getTraceId());
      contextData.put(SPAN_ID, spanContext.getSpanId());
      contextData.put(TRACE_FLAGS, spanContext.getTraceFlags().asHex());
    }

    if (addBaggage) {
      Baggage baggage = Baggage.fromContext(context);
      baggage.forEach((key, value) -> contextData.put(key, value.getValue()));
    }

    if (eventContext == null) {
      eventContext = contextData;
    } else {
      eventContext = new UnionMap<>(eventContext, contextData);
    }

    return new LoggingEventWrapper(event, eventContext);
  }

  @Override
  protected void append(ILoggingEvent event) {
    aai.appendLoopOnAppenders(wrapEvent(event));
  }

  @Override
  public void addAppender(Appender<ILoggingEvent> appender) {
    aai.addAppender(appender);
  }

  @Override
  public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
    return aai.iteratorForAppenders();
  }

  @Override
  public Appender<ILoggingEvent> getAppender(String name) {
    return aai.getAppender(name);
  }

  @Override
  public boolean isAttached(Appender<ILoggingEvent> appender) {
    return aai.isAttached(appender);
  }

  @Override
  public void detachAndStopAllAppenders() {
    aai.detachAndStopAllAppenders();
  }

  @Override
  public boolean detachAppender(Appender<ILoggingEvent> appender) {
    return aai.detachAppender(appender);
  }

  @Override
  public boolean detachAppender(String name) {
    return aai.detachAppender(name);
  }
}
