package fr.brouillard.gitbucket.announce.controller

import fr.brouillard.gitbucket.announce.html
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, SystemSettingsService}
import gitbucket.core.servlet.Database
import gitbucket.core.util.AdminAuthenticator
import gitbucket.core.view.helpers
import jp.sf.amateras.scalatra.forms._
import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail}
import org.pegdown.PegDownProcessor
import org.slf4j.LoggerFactory

class AnnounceController extends AnnounceControllerBase
with AdminAuthenticator

trait AnnounceControllerBase extends ControllerBase with AccountService {
  self:  AdminAuthenticator =>

  private val logger = LoggerFactory.getLogger(classOf[AnnounceController])

  case class AnnounceForm(content: String, subject: String)

  private val announceForm = mapping(
    "content" -> trim(label("Announce", text(required))),
    "subject" -> trim(label("Subject", text(required)))
  )(AnnounceForm.apply)

  get("/admin/announce")(adminOnly {
    html.announce(flash.get("info"))
  })

  post("/admin/announce/_preview", announceForm) { form =>
    contentType = "text/html"
    new PegDownProcessor().markdownToHtml(form.content)
  }

  post("/admin/announce", announceForm)(adminOnly { form =>
    flash += "info" -> "Announce has been sent."

    if (logger.isDebugEnabled) {
      logger.debug("sending announce: {}", form.content)
    }

    val systemSettings = new SystemSettingsService {}.loadSystemSettings
    if (systemSettings.notification && systemSettings.smtp.nonEmpty) {
      val email = new HtmlEmail
      val smtp = systemSettings.smtp.get

      email.setHostName(smtp.host)
      email.setSmtpPort(smtp.port.get)
      smtp.user.foreach { user =>
        email.setAuthenticator(new DefaultAuthenticator(user, smtp.password.getOrElse("")))
      }
      smtp.ssl.foreach { ssl =>
        email.setSSLOnConnect(ssl)
      }
      smtp.fromAddress
        .map (_ -> smtp.fromName.orNull)
        .orElse (Some("notifications@gitbucket.com" -> context.loginAccount.get.userName))
        .foreach { case (address, name) =>
        email.setFrom(address, name)
      }
      email.setCharset("UTF-8")
      email.setSubject(form.subject)

      email.setHtmlMsg(new PegDownProcessor().markdownToHtml(form.content))

      logger.info("sending email: {}", form.content)
      val database = Database()
      database withSession { implicit session =>
        getAllUsers(false).filter(account => !account.isGroupAccount && account.mailAddress.nonEmpty).foreach(account => email.addBcc(account.mailAddress))
      }

      email.send()
    }

    redirect("/admin/announce")
  })
}