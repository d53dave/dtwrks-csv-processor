package messagebroker

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.kafka.ConsumerSettings
import akka.kafka.ConsumerMessage
import akka.kafka.scaladsl.Consumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import akka.kafka.Subscriptions
import com.typesafe.config.ConfigFactory

/** 
 * This constructs an akka.stream.source connected to Kafka. 
 */
object CSVUploadSource {
  def create(groupId: String)(implicit system: ActorSystem): Source[ConsumerMessage.CommittableMessage[Array[Byte], String], Consumer.Control] = {
    val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new StringDeserializer)
      .withBootstrapServers(ConfigFactory.load().getString("messagebroker.urls"))
      .withGroupId(groupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

    val topic = ConfigFactory.load().getString("messagebroker.topic")
    Consumer.committableSource(consumerSettings, Subscriptions.topics(topic))
  }
}