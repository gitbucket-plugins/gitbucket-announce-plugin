package fr.brouillard.gitbucket.announce.service

import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.model.Account
import gitbucket.core.model.Profile.{Accounts, GroupMembers}
import gitbucket.core.service.AccountService

object EmailAddress {
  private val EmailRegex = """\b[a-zA-Z0-9.!#$%&¡¯*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*\b""".r
  def isValid(email: String): Boolean = EmailRegex.pattern.matcher(email.toUpperCase).matches()
}

trait AnnounceService {
  self: AccountService =>

  def getAccountByGroupName(groupName: String)(implicit s: Session): List[Account] = {
    val needs = GroupMembers
      .filter(_.groupName === groupName.bind)
      .sortBy(_.userName)
      .map(_.userName)
      .list

    Accounts
      .filter(t => (t.userName inSetBind needs) && (t.removed === false.bind))
      .list
  }

  def getTargetAddresses(to: String)(implicit s: Session): List[String] = {
    to.split(",").map(_.trim).flatMap { groupAccount =>
      val userMailAddress: List[Account] = if(groupAccount.toUpperCase == "ALL"){
        getAllUsers(false)
      } else {
        getAccountByGroupName(groupAccount)
      }
      userMailAddress.collect { case x
        if !x.isGroupAccount && x.mailAddress.nonEmpty =>
          x.mailAddress :: getAccountExtraMailAddresses(x.userName)
      }
    }
    .flatten
    .filter(mail => EmailAddress.isValid(mail))
    .distinct
    .toList
  }
}