package models

import play.api.libs.json.Writes
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.JsPath
import play.api.libs.functional.syntax._

case class CSVUpload(id: String, filename: String, path: String)

object CSVUpload {
  implicit val csvUploadJsonWrite = new Writes[CSVUpload] {
    def writes(csvUpload: CSVUpload) = Json.obj(
      "id" -> csvUpload.id,
      "filename" -> csvUpload.filename,
      "path" -> csvUpload.path)
  }
  
  implicit val csvUploadReads: Reads[CSVUpload] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "filename").read[String] and
    (JsPath \ "path").read[String]    
  ) (CSVUpload.apply _)
}
