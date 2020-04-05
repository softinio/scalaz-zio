/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import zio.Cause
import zio.Fiber
import zio.internal.tracing.TracingConfig

/**
 * A `Platform` provides the minimum capabilities necessary to bootstrap
 * execution of `ZIO` tasks.
 */
trait Platform { self =>

  /**
   * Retrieves the default executor.
   */
  def executor: Executor

  def withExecutor(e: Executor): Platform =
    new Platform.Proxy(self) {
      override def executor: Executor = e
    }

  /**
   * ZIO Tracing configuration.
   */
  def tracing: Tracing

  def withTracing(t: Tracing): Platform =
    new Platform.Proxy(self) {
      override def tracing: Tracing = t
    }

  def withTracingConfig(config: TracingConfig): Platform =
    new Platform.Proxy(self) {
      override val tracing: Tracing = self.tracing.copy(tracingConfig = config)
    }

  /**
   * Determines if a throwable is fatal or not. It is important to identify
   * these as it is not recommended to catch, and try to recover from, any
   * fatal error.
   */
  def fatal(t: Throwable): Boolean

  def withFatal(f: Throwable => Boolean): Platform =
    new Platform.Proxy(self) {
      override def fatal(t: Throwable): Boolean = f(t)
    }

  /**
   * Reports a fatal error.
   */
  def reportFatal(t: Throwable): Nothing

  def withReportFatal(f: Throwable => Nothing): Platform =
    new Platform.Proxy(self) {
      override def reportFatal(t: Throwable): Nothing = f(t)
    }

  /**
   * Reports the specified failure.
   */
  def reportFailure(cause: Cause[Any]): Unit

  /**
   * Print Fiber Dump of all fibers when interrupt signal received
   */
  def onInterruptSignal: Platform =
    new Platform.Proxy(self) {
      withInterruptHandler {
        Fiber.dumpAllStr.map(fibs => println(fibs))
        ()
      }
    }

  /**
   * Use SIGUSR2 signal to interrupt runtime
   *
   * @param handler to run when SIGUSR2 is received
   */
  def withInterruptHandler(handler: Unit): Boolean =
    withSignalHandler("USR2")(handler)

  def withReportFailure(f: Cause[Any] => Unit): Platform =
    new Platform.Proxy(self) {
      override def reportFailure(cause: Cause[Any]): Unit = f(cause)
    }

  /**
   * Add generic signal handler
   *
   * @param signal to listen for
   * @param handler to run on receiving signal
   */
  def withSignalHandler(signal: String)(handler: Unit): Boolean =
    Platform.addSignalHandler(signal)(() => handler)

}
object Platform extends PlatformSpecific {
  abstract class Proxy(self: Platform) extends Platform {
    def executor: Executor                           = self.executor
    def tracing: Tracing                             = self.tracing
    def fatal(t: Throwable): Boolean                 = self.fatal(t)
    def reportFatal(t: Throwable): Nothing           = self.reportFatal(t)
    def reportFailure(cause: Cause[Any]): Unit       = self.reportFailure(cause)
    def fiberDumpOnInterrupt(handler: Unit): Boolean = self.withInterruptHandler(handler)
  }
}
