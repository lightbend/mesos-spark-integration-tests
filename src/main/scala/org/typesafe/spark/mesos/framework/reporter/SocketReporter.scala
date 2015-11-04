package org.typesafe.spark.mesos.framework.reporter

import java.io.{PrintStream, ObjectOutputStream}
import java.net.{InetAddress, Socket}

import org.scalatest.ResourcefulReporter
import org.scalatest.events.Event

//Essentially a clone of scalatest.SocketReporter
class SocketReporter(runnerAddress: InetAddress, port: Int) extends ResourcefulReporter {
  private val socket = new Socket(runnerAddress, port)
  private val writer = new PrintStream(socket.getOutputStream)

  def apply(event: Event) {
    synchronized {
      writer.println(event)
      writer.flush()
    }
  }

  def dispose() {
    writer.flush()
    writer.close()
    socket.close()
  }
}
