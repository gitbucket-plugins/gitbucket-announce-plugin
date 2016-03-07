package fr.brouillard.gitbucket.announce.controller

import fr.brouillard.gitbucket.announce.html
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, SystemSettingsService}
import gitbucket.core.servlet.Database
import gitbucket.core.util.AdminAuthenticator
import io.github.gitbucket.scalatra.forms._
import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail, EmailException}
import io.github.gitbucket.markedj.Marked
import io.github.gitbucket.markedj.Options
import org.slf4j.LoggerFactory
import gitbucket.core.model.{GroupMember, Account}
import gitbucket.core.model.Profile._
import gitbucket.core.util.{StringUtil, LDAPUtil}
import gitbucket.core.service.SystemSettingsService.SystemSettings
import profile.simple._
import StringUtil._
import org.slf4j.LoggerFactory
import gitbucket.core.model.Profile.dateColumnType
import javax.mail.SendFailedException

class AnnounceController extends AnnounceControllerBase
with AdminAuthenticator

trait AnnounceControllerBase extends ControllerBase with AccountService {
  self:  AdminAuthenticator =>

  private val logger = LoggerFactory.getLogger(classOf[AnnounceController])

  object EmailAddress {
    def isValid(email: String): Boolean = EmailRegex.pattern.matcher(email.toUpperCase).matches()

    private val EmailRegex = """\b[a-zA-Z0-9.!#$%&¡¯*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*\b""".r
  }

  def getAccountByGroupName(groupName: String, includeRemoved: Boolean = false)(implicit s: Session): List[Account] = {
    val needs = GroupMembers
      .filter(_.groupName === groupName.bind)
      .sortBy(_.userName)
      .map(_.userName)
      .list

    Accounts
      .filter(t => (t.userName inSetBind needs) && (t.removed === false.bind, !includeRemoved))
      .list
  }

  case class AnnounceForm(content: String, subject: String, to: String)

  private val announceForm = mapping(
    "content" -> trim(label("Announce", text(required))),
    "subject" -> trim(label("Subject", text(required))),
    "to" -> trim(label("To", text(required)))
  )(AnnounceForm.apply)

  get("/admin/announce")(adminOnly {
    html.announce(flash.get("info"))
  })

  post("/admin/announce", announceForm)(adminOnly { form =>

    if (logger.isDebugEnabled) {
      logger.debug("sending announce: {}", form.content)
    }

    val systemSettings = new SystemSettingsService {}.loadSystemSettings
    if (systemSettings.useSMTP && systemSettings.smtp.nonEmpty) {
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
      email.setSendPartial(true);

      val opts = new Options();
      opts.setSanitize(true);

      email.setHtmlMsg(Marked.marked(form.content, opts))

      logger.debug("sending email subject: {}", form.subject)
      logger.debug("sending email content: {}", form.content)
      val database = Database()
      database withSession { implicit session =>
        val mailto = form.to.split(",").map(_.trim).map(groupaccount => {
            val userMailAddress :List[Account] = if(groupaccount.toUpperCase == "ALL")  getAllUsers(false) else getAccountByGroupName(groupaccount)
            userMailAddress.filter(account => !account.isGroupAccount && account.mailAddress.nonEmpty && EmailAddress.isValid(account.mailAddress))  
          }    
        ).flatMap(a => a).map(_.mailAddress).toSet//.foreach(account => email.addBcc(account.mailAddress)
        logger.debug("sending email to: {}", form.to)
        logger.debug("sending email to EmailAddress: {}", mailto.mkString(","))
        //getAllUsers(false).filter(account => !account.isGroupAccount && account.mailAddress.nonEmpty && EmailAddress.isValid(account.mailAddress)).foreach(account => email.addBcc(account.mailAddress))
        mailto.foreach(mailAddress => email.addBcc(mailAddress))
      }

      try {
    	  email.send()
          flash += "info" -> "Announce has been sent."
      } catch {
        case t:  EmailException => {
          t.getCause match {
            case ex: SendFailedException => {
            	logger.error("found invalid email address while sending notification", ex)
            	if (ex.getInvalidAddresses() != null) {
            		for (ia <- ex.getInvalidAddresses()) {
            			logger.error("invalid email address: {}", ia.toString())
            		}
            	}
            	if (ex.getValidUnsentAddresses() != null) {
            		for (ua <- ex.getValidUnsentAddresses()) {
            			logger.error("email not sent to: {}", ua.toString())
            		}
            	}
                flash += "info" -> "Announce has been sent."
            }
            case _ => {
            	logger.error("failure sending email", t)
                flash += "info" -> "Announce cannot be sent, verify log errors."
            }
          }
        }
        case e: Exception => {
          logger.error("unexpected exception while sending email", e)
          flash += "info" -> "Announce cannot be sent, verify log errors."
        }
      }
    } else {
      flash += "info" -> "Announce cannot be sent, verify SMTP settings"
    }

    redirect("/admin/announce")
  })
}