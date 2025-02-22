/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava.v3.common.AbstractRxJava3WithSpanTest
import io.opentelemetry.instrumentation.rxjava.v3.common.AbstractTracedWithSpan
import io.opentelemetry.instrumentation.rxjava.v3.common.instrumentationannotation.TracedWithSpan

class RxJava3InstrumentationWithSpanTest extends AbstractRxJava3WithSpanTest {

  @Override
  AbstractTracedWithSpan newTraced() {
    return new TracedWithSpan()
  }
}
