/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package zio.zmx

import collection.JavaConverters._
import scala.collection.mutable._
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.Iterator

object ZMXServer {
  private def register(selector: Selector, serverSocket: ServerSocketChannel) = {
    val client: SocketChannel = serverSocket.accept()
    client.configureBlocking(false)
    client.register(selector, SelectionKey.OP_READ)
  }

  def responseReceived(buffer: ByteBuffer, key: SelectionKey, debug: Boolean): Boolean = {
    val client: SocketChannel = key.channel().asInstanceOf[SocketChannel]
    client.read(buffer)
    val received: String = ZMXCommands.ByteBufferToString(buffer)
    if (debug)
      println(s"Server received: $received")
    if (received == "STOP_SERVER") {
      println("closing client channel")
      client.close()
      return false
    }
    buffer.flip
    client.write(ZMXCommands.StringToByteBuffer(s"Server received: $received"))
    buffer.clear
    true
  }

  def apply(config: ZMXConfig, selector: Selector, zmxSocket: ServerSocketChannel, zmxAddress: InetSocketAddress): Unit = {
    zmxSocket.socket.setReuseAddress(true)
    zmxSocket.bind(zmxAddress)
    zmxSocket.configureBlocking(false)
    zmxSocket.register(selector, SelectionKey.OP_ACCEPT)
    val buffer: ByteBuffer = ByteBuffer.allocate(256)

    var state: Boolean = true
    while (state) {
      selector.select()
      val zmxKeys: Set[SelectionKey] = selector.selectedKeys.asScala
      val zmxIter: Iterator[SelectionKey] = zmxKeys.iterator.asJava
      while (zmxIter.hasNext) {
        val currentKey: SelectionKey = zmxIter.next
        if (currentKey.isAcceptable) {
          register(selector, zmxSocket)
        } 
        if (currentKey.isReadable) {
          state = responseReceived(buffer, currentKey, config.debug)
          if (state == false) {
            println("Closing socket")
            zmxSocket.close()
            selector.close()
          }
        }
        zmxIter.remove()
      }
    } 
  }
}
