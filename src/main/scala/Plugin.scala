import com.gatewify.assets
import io.github.gitbucket.solidbase.model.Version

class Plugin extends gitbucket.core.plugin.Plugin {
  override val pluginId: String = "assetsgw"
  override val pluginName: String = "Gatewify Assets Plugin"
  override val description: String = "Adds the method latest and latest.zip for assets"
  override val versions: List[Version] = List(new Version("1.0.0"))

  override val controllers = Seq(
    "/*" -> new ControllerAssets()
  )
}
