package com.karasiq.scalajsbundler

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Path, _}

import com.karasiq.scalajsbundler.ScalaJSBundler.Bundle
import com.karasiq.scalajsbundler.compilers.AssetCompilers
import com.karasiq.scalajsbundler.dsl.BundlerDsl
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import scala.collection.mutable.ListBuffer

object ScalaJSBundlerPlugin extends AutoPlugin {
  private def clearDirectory(destDir: Path): Unit = {
    if (Files.isSymbolicLink(destDir) && Files.isRegularFile(destDir)) {
      Files.delete(destDir)
    } else if (Files.isDirectory(destDir)) {
      Files.walkFileTree(destDir, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        }
      })
    }
  }

  private def fileList(destDir: Path): Seq[File] = {
    val buffer = new ListBuffer[File]()
    Files.walkFileTree(destDir, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        buffer += file.toFile
        FileVisitResult.CONTINUE
      }
    })

    buffer.result()
  }

  object autoImport extends BundlerDsl {
    val scalaJsBundlerAssets = settingKey[Seq[Bundle]]("Scala.js bundler resources")
    val scalaJsBundlerDest = settingKey[File]("Scala.js bundler output directory")
    val scalaJsBundlerCompilers = settingKey[AssetCompilers]("Scala.js asset compilers")
    val scalaJsBundlerCompile = taskKey[Seq[File]]("Compiles Scala.js bundles")

    lazy val baseScalaJsBundlerSettings: Seq[Def.Setting[_]] = Seq(
      scalaJsBundlerAssets := Nil,
      scalaJsBundlerDest := resourceManaged.value / "webapp",
      scalaJsBundlerCompilers := AssetCompilers.default,
      scalaJsBundlerCompile <<= (scalaJsBundlerAssets, scalaJsBundlerDest, scalaJsBundlerCompilers, streams).map { (src, dest, compilers, streams) ⇒
        streams.log.info("Compiling Scala.js assets")
        clearDirectory(dest.toPath)
        val compiler = new ScalaJSBundleCompiler
        src.foreach { case Bundle(page, contents @ _*) ⇒
          compiler.createHtml(compilers, dest.toString, page, contents)
        }
        fileList(dest.toPath)
      },
      managedResources ++= scalaJsBundlerCompile.value
    )
  }

  import autoImport._

  override def requires: Plugins = JvmPlugin

  override val projectSettings = inConfig(Compile)(baseScalaJsBundlerSettings)
}
