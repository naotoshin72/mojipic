package infrastructure.twitter

import javax.inject.Inject
import play.api.Configuration
import play.api.cache.SyncCacheApi
import twitter4j.{Twitter, TwitterFactory}
import twitter4j.auth.AccessToken

import scala.concurrent.duration._
import scala.util.control.NonFatal

class TwitterAuthenticator @Inject() (configuration: Configuration, cache: SyncCacheApi){

  val CacheKeyPrefixTwitter = "twitterInstance"

  val ConsumerKey = configuration.get[String]("mojipic.consumerkey")
  val ConsumerSecret = configuration.get[String]("mojipic.consumersecret")

  private[this] def cacheKeyTwitter(sessionId: String): String = CacheKeyPrefixTwitter + sessionId

  def startAuthentication(sessionId: String, callBackUrl: String): String = {
    try {
      val twitter = new TwitterFactory().getInstance()
      twitter.setOAuthConsumer(ConsumerKey, ConsumerSecret)

      val requestToken = twitter.getOAuthRequestToken(callBackUrl)
      cache.set(cacheKeyTwitter(sessionId), twitter, 30.seconds)

      requestToken.getAuthenticationURL
    } catch {
      case NonFatal(e) =>
        throw TwitterException(s"Could not get an access token. SessionId: $sessionId", e)
    }
  }

  def getAccessToken(sessionId: String, verifier: String): AccessToken = {
    try {
      cache.get[Twitter](cacheKeyTwitter(sessionId)).get.getOAuthAccessToken(verifier)
    } catch {
      case NonFatal(e) =>
        throw TwitterException(s"Could not get an access token. SessionId: $sessionId", e)
    }
  }
}

case class TwitterException(message: String = null, cause: Throwable = null) extends RuntimeException(message, cause)