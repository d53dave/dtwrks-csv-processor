package common

import scalikejdbc._, SQLInterpolation._
import scalikejdbc.AutoSession
import models.Event

object DBUtil {
  val ddl = io.Source.fromInputStream(this.getClass.getResourceAsStream("/schema.sql"), "UTF-8").getLines.mkString
  
  def dropDB(implicit session: DBSession): Unit = {
    sql"drop table events if exists".execute.apply
    ()
  }

  def createDB(implicit session: DBSession): Unit = {
    SQL(ddl).execute.apply()
    ()
  }
   
  def getEventsFromDb(implicit session: DBSession): Seq[Event] = {
   sql"select * from events".map(rs => Event(rs)).list.apply()
  }
  
  def truncateEvents(implicit session: DBSession): Unit = {
   sql"delete from events".execute.apply()
   ()
  }
}