package controllers.admin.users

import controllers.BaseController
import javax.inject.Inject
import models.document.DocumentService
import models.user.Roles._
import models.user.UserService
import play.api.cache.CacheApi
import scala.concurrent.{ ExecutionContext, Future }
import storage.DB

class UserAdminController @Inject() (implicit val cache: CacheApi, val db: DB, ec: ExecutionContext) extends BaseController {
  
  def index = AsyncStack(AuthorityKey -> Admin) { implicit request =>
    UserService.listUsers().map { users => 
      Ok(views.html.admin.users.index(users))   
    }
  }
  
  def showDetails(username: String) = AsyncStack(AuthorityKey -> Admin) { implicit request =>
    UserService.findByUsername(username).flatMap(_ match {
      
      case Some(user) =>
        DocumentService.findByOwner(username).map(documents =>
          Ok(views.html.admin.users.details(user, documents)))
      
      case None => Future.successful(NotFound)
      
    })
  }
  
}