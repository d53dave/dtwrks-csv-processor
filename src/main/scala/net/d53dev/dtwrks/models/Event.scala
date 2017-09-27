package models

import org.joda.time.DateTime
import com.typesafe.config.ConfigFactory
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTimeZone
import scala.util.Try
import scalikejdbc._

case class Event(id: Long, name: String, timeOfStart: DateTime)

object Event extends SQLSyntaxSupport[Event] {
  val formatStr = ConfigFactory.load().getString("processor.date.format")
  val formatter: DateTimeFormatter = DateTimeFormat.forPattern(formatStr);
  val utc = DateTimeZone.forID("UTC");

  def apply(tokens: Seq[String]): Option[Event] = {
    Try {
      val dt: DateTime = formatter.parseDateTime(tokens(2));
      val start = dt.toDateTime(utc)
      
      if(tokens.init.forall(_.length() > 0)) Some(new Event(tokens(0).toLong, tokens(1), start))
      else None
    }.getOrElse(None)
  }
  
  override val tableName = "events"
  def apply(rs: WrappedResultSet) = new Event(
    rs.long("id"), rs.string("name"), rs.jodaDateTime("time_of_start"))
}
