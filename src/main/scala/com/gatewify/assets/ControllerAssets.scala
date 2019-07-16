package com.gatewify.assets;

import gitbucket.core.api._
import gitbucket.core.controller._
import gitbucket.core.controller.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.controller.RepositoryViewerController

class ControllerAssets extends ControllerBase {
	get("/api/v3/repos/:owner/:repository/releases/:tag/assets/latest")(referrersOnly { repository =>
		val name = params("tag")
		getReleaseAsset(repository.owner, repository.name, name, fileId)
		.map { asset =>
			JsonFormat(ApiReleaseAsset(asset, RepositoryName(repository)))
		}
		.getOrElse(archiveRepository(name, repository, ""))
	})
	get("/api/v3/repos/:owner/:repository/releases/:tag/assets/latest.zip")(referrersOnly { repository =>
		val name = params("tag")
		getReleaseAsset(repository.owner, repository.name, name, fileId)
		.map { asset =>
			JsonFormat(ApiReleaseAsset(asset, RepositoryName(repository)))
		}
		.getOrElse(archiveRepository(name, repository, ""))
	})

}
