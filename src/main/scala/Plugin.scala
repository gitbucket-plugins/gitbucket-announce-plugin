import javax.servlet.ServletContext

import fr.brouillard.gitbucket.announce.controller.AnnounceController
import gitbucket.core.plugin.PluginRegistry
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.util.Version

class Plugin extends gitbucket.core.plugin.Plugin {
  override val pluginId: String = "announce"

  override val pluginName: String = "Announce Plugin"

  override val description: String = "Allows to handle announces for gitbucket"

  override val versions: List[Version] = List(
    Version(1, 3)
    , Version(1, 2)
    , Version(1, 1)
    , Version(1, 0)
  )

  override def javaScripts(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Seq[(String, String)] = {
    // Add Snippet link to the header
    val path = settings.baseUrl.getOrElse(context.getContextPath)
    Seq(
      ".*/admin/announce" -> s"""
        |$$('#system-admin-menu-container>li:last').after(
        |  $$('<li class="active"><a href="${path}/admin/announce">Global Announce</a></li>')
        |);
      """.stripMargin,
      ".*/admin/(?!announce).*" -> s"""
        |$$('#system-admin-menu-container>li:last').after(
        |  $$('<li><a href="${path}/admin/announce">Global Announce</a></li>')
        |);
      """.stripMargin
    )
  }

  override val controllers = Seq(
    "/admin/announce" -> new AnnounceController()
  )
}
