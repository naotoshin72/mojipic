package domain.repository


import java.time.LocalDateTime
import domain.entity.{PictureId, PictureProperty, TwitterId}

import scala.concurrent.Future

trait PicturePropertyRepository {

  def create(value: PictureProperty.Value): Future[PictureId]
  def find(pictureId: PictureId): Future[PictureProperty]
  def update(pictureId: PictureId, pictureProperty: PictureProperty.Value): Future[Unit]

  def findAllByTwitterIdAndDateTime(twitterId: TwitterId, lastCreatedTime: LocalDateTime): Future[Seq[PictureProperty]]
  def findAllByDateTime(lastCreatedTime: LocalDateTime): Future[Seq[PictureProperty]]
}
