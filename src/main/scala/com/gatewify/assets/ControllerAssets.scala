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

class ControllerAssets extends ControllerBase {
	self: AccountService with ReleaseService with ReferrerAuthenticator with WritableUsersAuthenticator with ApiReleaseControllerBase =>
	get("/api/v3/repos/:owner/:repository/releases/:tag/assets/latest")(referrersOnly { repository =>
		val name = params("tag")
		val assets = getReleaseAssets(repository.owner, repository.name, name)
		assets.map{
			release =>
				defining(FileUtil.generateFileId) { fileId =>
					val buf = new Array[Byte](request.inputStream.available())
					request.inputStream.read(buf)
					FileUtils.writeByteArrayToFile(
						new File(
							getReleaseFilesDir(repository.owner, repository.name),
						FileUtil.checkFilename(tag + "/" + fileId)
						),
					buf
				)
				createReleaseAsset(
					repository.owner,
					repository.name,
					tag,
					fileId,
					name,
					request.contentLength.getOrElse(0),
					context.loginAccount.get
				)
				val asset = getReleaseAsset(repository.owner, repository.name, tag, fileId)
				(for {
				  _ <- repository.tags.find(_.name == tagName)
				  _ <- getRelease(repository.owner, repository.name, tagName)
				  asset <- getReleaseAsset(repository.owner, repository.name, tagName, fileId)
				} yield {
				  response.setHeader("Content-Disposition", s"attachment; filename=${asset.label}")
				  return RawData(
					FileUtil.getSafeMimeType(asset.label),
					new File(getReleaseFilesDir(repository.owner, repository.name), FileUtil.checkFilename(tagName + "/" + fileId))
				  )
				})
				.getOrElse {
					val asset_id=name+".zip"
					archiveRepository(asset_id, repository, "")
				}
			}
		}
		// not found one? no problem, return the latest asset
		.getOrElse{
			val asset_id=name+".zip"
			archiveRepository(asset_id, repository, "")
		}
	})
	get("/api/v3/repos/:owner/:repository/releases/:tag/assets/latest.zip")(referrersOnly { repository =>
		val name = params("tag")
		val assets = getReleaseAssets(repository.owner, repository.name, name)
		assets.map{
			release =>
				defining(FileUtil.generateFileId) { fileId =>
					val buf = new Array[Byte](request.inputStream.available())
					request.inputStream.read(buf)
					FileUtils.writeByteArrayToFile(
						new File(
							getReleaseFilesDir(repository.owner, repository.name),
						FileUtil.checkFilename(tag + "/" + fileId)
						),
					buf
				)
				createReleaseAsset(
					repository.owner,
					repository.name,
					tag,
					fileId,
					name,
					request.contentLength.getOrElse(0),
					context.loginAccount.get
				)
				val asset = getReleaseAsset(repository.owner, repository.name, tag, fileId)
				(for {
				  _ <- repository.tags.find(_.name == tagName)
				  _ <- getRelease(repository.owner, repository.name, tagName)
				  asset <- getReleaseAsset(repository.owner, repository.name, tagName, fileId)
				} yield {
				  response.setHeader("Content-Disposition", s"attachment; filename=${asset.label}")
				  return RawData(
					FileUtil.getSafeMimeType(asset.label),
					new File(getReleaseFilesDir(repository.owner, repository.name), FileUtil.checkFilename(tagName + "/" + fileId))
				  )
				})
				.getOrElse {
					val asset_id=name+".zip"
					archiveRepository(asset_id, repository, "")
				}
			}
		}
		// not found one? no problem, return the latest asset
		.map { asset =>
			JsonFormat(ApiReleaseAsset(asset, RepositoryName(repository)))
		}
		.getOrElse{
			val asset_id=name+".zip"
			archiveRepository(asset_id, repository, "")
		}
	})
	
	private def archiveRepository(
    filename: String,
    repository: RepositoryService.RepositoryInfo,
    path: String
  ) = {
    def archive(revision: String, archiveFormat: String, archive: ArchiveOutputStream)(
      entryCreator: (String, Long, java.util.Date, Int) => ArchiveEntry
    ): Unit = {
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        val oid = git.getRepository.resolve(revision)
        val commit = JGitUtil.getRevCommitFromId(git, oid)
        val date = commit.getCommitterIdent.getWhen
        val sha1 = oid.getName()
        val repositorySuffix = (if (sha1.startsWith(revision)) sha1 else revision).replace('/', '-')
        val pathSuffix = if (path.isEmpty) "" else '-' + path.replace('/', '-')
        val baseName = repository.name + "-" + repositorySuffix + pathSuffix

        using(new TreeWalk(git.getRepository)) { treeWalk =>
          treeWalk.addTree(commit.getTree)
          treeWalk.setRecursive(true)
          if (!path.isEmpty) {
            treeWalk.setFilter(PathFilter.create(path))
          }
          if (treeWalk != null) {
            while (treeWalk.next()) {
              val entryPath =
                if (path.isEmpty) baseName + "/" + treeWalk.getPathString
                else path.split("/").last + treeWalk.getPathString.substring(path.length)
              val size = JGitUtil.getContentSize(git.getRepository.open(treeWalk.getObjectId(0)))
              val mode = treeWalk.getFileMode.getBits
              val entry: ArchiveEntry = entryCreator(entryPath, size, date, mode)
              JGitUtil.openFile(git, repository, commit.getTree, treeWalk.getPathString) { in =>
                archive.putArchiveEntry(entry)
                IOUtils.copy(
                  EolStreamTypeUtil.wrapInputStream(
                    in,
                    EolStreamTypeUtil
                      .detectStreamType(
                        OperationType.CHECKOUT_OP,
                        git.getRepository.getConfig.get(WorkingTreeOptions.KEY),
                        treeWalk.getAttributes
                      )
                  ),
                  archive
                )
                archive.closeArchiveEntry()
              }
            }
          }
        }
      }
    }

    val suffix =
      path.split("/").lastOption.collect { case x if x.length > 0 => "-" + x.replace('/', '_') }.getOrElse("")
    val zipRe = """(.+)\.zip$""".r
    val tarRe = """(.+)\.tar\.(gz|bz2|xz)$""".r

    filename match {
      case zipRe(revision) =>
        response.setHeader(
          "Content-Disposition",
          s"attachment; filename=${repository.name}-${revision}${suffix}.zip"
        )
        contentType = "application/octet-stream"
        response.setBufferSize(1024 * 1024)
        using(new ZipArchiveOutputStream(response.getOutputStream)) { zip =>
          archive(revision, ".zip", zip) { (path, size, date, mode) =>
            val entry = new ZipArchiveEntry(path)
            entry.setUnixMode(mode)
            entry.setTime(date.getTime)
            entry
          }
        }
        ()
      case tarRe(revision, compressor) =>
        response.setHeader(
          "Content-Disposition",
          s"attachment; filename=${repository.name}-${revision}${suffix}.tar.${compressor}"
        )
        contentType = "application/octet-stream"
        response.setBufferSize(1024 * 1024)
        using(compressor match {
          case "gz"  => new GzipCompressorOutputStream(response.getOutputStream)
          case "bz2" => new BZip2CompressorOutputStream(response.getOutputStream)
          case "xz"  => new XZCompressorOutputStream(response.getOutputStream)
        }) { compressorOutputStream =>
          using(new TarArchiveOutputStream(compressorOutputStream)) { tar =>
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR)
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
            tar.setAddPaxHeadersForNonAsciiNames(true)
            archive(revision, ".tar.gz", tar) { (path, size, date, mode) =>
              val entry = new TarArchiveEntry(path)
              entry.setModTime(date)
              entry.setMode(mode)
              entry
            }
          }
        }
        ()
      case _ =>
        NotFound()
    }
  }

}
