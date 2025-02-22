/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaInstrumenterFactory {

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.getDefault();
  private List<String> capturedHeaders = emptyList();
  private boolean captureExperimentalSpanAttributes = false;
  private boolean messagingReceiveInstrumentationEnabled = false;

  public KafkaInstrumenterFactory(OpenTelemetry openTelemetry, String instrumentationName) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setErrorCauseExtractor(ErrorCauseExtractor errorCauseExtractor) {
    this.errorCauseExtractor = errorCauseExtractor;
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setCapturedHeaders(List<String> capturedHeaders) {
    this.capturedHeaders = capturedHeaders;
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /**
   * @deprecated if you have a need for this configuration option please open an issue in the <a
   *     href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues">opentelemetry-java-instrumentation</a>
   *     repository.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setPropagationEnabled(boolean propagationEnabled) {
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setMessagingReceiveInstrumentationEnabled(
      boolean messagingReceiveInstrumentationEnabled) {
    this.messagingReceiveInstrumentationEnabled = messagingReceiveInstrumentationEnabled;
    return this;
  }

  public Instrumenter<ProducerRecord<?, ?>, RecordMetadata> createProducerInstrumenter() {
    return createProducerInstrumenter(Collections.emptyList());
  }

  public Instrumenter<ProducerRecord<?, ?>, RecordMetadata> createProducerInstrumenter(
      Iterable<AttributesExtractor<ProducerRecord<?, ?>, RecordMetadata>> extractors) {

    KafkaProducerAttributesGetter getter = KafkaProducerAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.SEND;

    return Instrumenter.<ProducerRecord<?, ?>, RecordMetadata>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            buildMessagingAttributesExtractor(getter, operation, capturedHeaders))
        .addAttributesExtractors(extractors)
        .addAttributesExtractor(new KafkaProducerAttributesExtractor())
        .setErrorCauseExtractor(errorCauseExtractor)
        .buildInstrumenter(SpanKindExtractor.alwaysProducer());
  }

  public Instrumenter<ConsumerAndRecord<ConsumerRecords<?, ?>>, Void>
      createConsumerReceiveInstrumenter() {
    KafkaReceiveAttributesGetter getter = KafkaReceiveAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.RECEIVE;

    return Instrumenter.<ConsumerAndRecord<ConsumerRecords<?, ?>>, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            buildMessagingAttributesExtractor(getter, operation, capturedHeaders))
        .addAttributesExtractor(KafkaReceiveAttributesExtractor.INSTANCE)
        .setErrorCauseExtractor(errorCauseExtractor)
        .setEnabled(messagingReceiveInstrumentationEnabled)
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public Instrumenter<ConsumerAndRecord<ConsumerRecord<?, ?>>, Void>
      createConsumerProcessInstrumenter() {
    return createConsumerOperationInstrumenter(MessageOperation.PROCESS, Collections.emptyList());
  }

  public Instrumenter<ConsumerAndRecord<ConsumerRecord<?, ?>>, Void>
      createConsumerOperationInstrumenter(
          MessageOperation operation,
          Iterable<AttributesExtractor<ConsumerAndRecord<ConsumerRecord<?, ?>>, Void>> extractors) {

    KafkaConsumerAttributesGetter getter = KafkaConsumerAttributesGetter.INSTANCE;

    InstrumenterBuilder<ConsumerAndRecord<ConsumerRecord<?, ?>>, Void> builder =
        Instrumenter.<ConsumerAndRecord<ConsumerRecord<?, ?>>, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(getter, operation, capturedHeaders))
            .addAttributesExtractor(new KafkaConsumerAttributesExtractor())
            .addAttributesExtractors(extractors)
            .setErrorCauseExtractor(errorCauseExtractor);
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
    }

    if (messagingReceiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(
          new PropagatorBasedSpanLinksExtractor<>(
              openTelemetry.getPropagators().getTextMapPropagator(),
              KafkaConsumerRecordGetter.INSTANCE));
      return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      return builder.buildConsumerInstrumenter(KafkaConsumerRecordGetter.INSTANCE);
    }
  }

  public Instrumenter<ConsumerAndRecord<ConsumerRecords<?, ?>>, Void>
      createBatchProcessInstrumenter() {
    KafkaReceiveAttributesGetter getter = KafkaReceiveAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;

    return Instrumenter.<ConsumerAndRecord<ConsumerRecords<?, ?>>, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            buildMessagingAttributesExtractor(getter, operation, capturedHeaders))
        .addAttributesExtractor(KafkaReceiveAttributesExtractor.INSTANCE)
        .addSpanLinksExtractor(
            new KafkaBatchProcessSpanLinksExtractor(
                openTelemetry.getPropagators().getTextMapPropagator()))
        .setErrorCauseExtractor(errorCauseExtractor)
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static <REQUEST, RESPONSE>
      AttributesExtractor<REQUEST, RESPONSE> buildMessagingAttributesExtractor(
          MessagingAttributesGetter<REQUEST, RESPONSE> getter,
          MessageOperation operation,
          List<String> capturedHeaders) {
    return MessagingAttributesExtractor.builder(getter, operation)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }
}
