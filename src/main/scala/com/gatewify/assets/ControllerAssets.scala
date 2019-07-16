package com.gatewify.assets;

import gitbucket.core.controller.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.controller.RepositoryViewerController

import java.io.{ByteArrayInputStream, File}
import java.io.File

import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, ReleaseService}
import gitbucket.core.util.Directory.getReleaseFilesDir
import gitbucket.core.util.{FileUtil, ReferrerAuthenticator, RepositoryName, WritableUsersAuthenticator}
import gitbucket.core.util.Implicits._
import gitbucket.core.util.SyntaxSugars.defining
import org.apache.commons.io.FileUtils
import org.scalatra.{Created, NoContent}

class ControllerAssets extends ControllerBase 
	with ApiReleaseControllerBase 
	with ReleaseControllerBase{
	self: AccountService with ReleaseService with ReferrerAuthenticator with WritableUsersAuthenticator =>
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
