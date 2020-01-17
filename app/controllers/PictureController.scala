package controllers

import java.io.File
import java.nio.file.{FileSystems, Files, Path, StandardCopyOption}
import java.time.{Clock, LocalDateTime}

import akka.stream.scaladsl.FileIO
import javax.inject.{Inject, Singleton}
import com.google.common.net.MediaType
import com.redis.RedisClient
import domain.entity.{PictureId, PictureProperty, TwitterId}
import domain.repository.PicturePropertyRepository
import infrastructure.redis.RedisKeys
import play.api.cache.SyncCacheApi
import play.api.http.HttpEntity
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import play.api.mvc.MultipartFormData.FilePart

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PictureController @Inject()(cc: ControllerComponents,
                                  clock: Clock,
                                  executionContext: ExecutionContext,
                                  picturePropertyRepository: PicturePropertyRepository,
                                  redisClient: RedisClient,
                                  val cache: SyncCacheApi,
                                 ) extends TwitterLoginController(cc) {


  implicit val ec = executionContext
  val originalStoreDirPath = "./filesystem/original"


  def post = TwitterLoginAction.async { request =>
    (request.accessToken, request.body.asMultipartFormData) match {
      case (Some(accessToken), Some(form)) =>
        form.file("file") match {
          case Some(file) =>
            val storeDirPath = FileSystems.getDefault.getPath(originalStoreDirPath)
            if (!Files.exists(storeDirPath)) Files.createDirectories(storeDirPath)

            val originalFilepath =  FileSystems.getDefault.getPath(storeDirPath.toString, System.currentTimeMillis().toString)
            Files.copy(file.ref.path, originalFilepath, StandardCopyOption.COPY_ATTRIBUTES)
            val propertyValue = createPicturePropertyValue(TwitterId(accessToken.getUserId), file, form, originalFilepath)

            val pictureId = picturePropertyRepository.create(propertyValue)
            pictureId.map({ (id) =>
              redisClient.rpush(RedisKeys.Tasks, id.value)
              Ok("picture uploaded")
            })


            Future.successful(Ok("Picture uploaded."))
          case _ => Future.successful(Unauthorized("Need picture data."))
        }
      case _ => Future.successful(Unauthorized("Need to login by Twitter and picture data."))
    }
  }

  def get(pictureId: Long) = Action.async { request =>
    val pictureProperty = picturePropertyRepository.find(PictureId(pictureId))
    pictureProperty.map(pictureProperty => {
      pictureProperty.value.convertedFilepath match {
        case Some(convertedFilePath) => {
          val file = new File(convertedFilePath)
          val source = FileIO.fromPath(file.toPath)
          Result(
            header = ResponseHeader(200, Map.empty),
            body = HttpEntity.Streamed(source, None, Some(pictureProperty.value.contentType.toString))
          )
        }
        case None => NotFound
      }
    })
  }

  private[this] def createPicturePropertyValue(
                                                twitterId: TwitterId,
                                                file: FilePart[TemporaryFile],
                                                form: MultipartFormData[TemporaryFile],
                                                originalFilePath: Path
                                              ): PictureProperty.Value = {
    val overlayText = form.dataParts.get("overlaytext").flatMap(_.headOption).getOrElse("")
    val overlayTextSize = form.dataParts.get("overlaytextsize").flatMap(_.headOption).getOrElse("60").toInt
    val contentType = MediaType.parse(file.contentType.getOrElse("application/octet-stream"))

    PictureProperty.Value(
      PictureProperty.Status.Converting,
      twitterId,
      file.filename,
      contentType,
      overlayText,
      overlayTextSize,
      Some(originalFilePath.toString),
      None,
      LocalDateTime.now(clock))
  }

}
