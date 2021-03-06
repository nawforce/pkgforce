/*
 [The "BSD licence"]
 Copyright (c) 2019 Kevin Jones
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.nawforce.common.documents

import com.nawforce.common.api._
import com.nawforce.common.diagnostics.{CatchingLogger, Issue}
import com.nawforce.common.names.TypeNames
import com.nawforce.common.path.PathLike
import com.nawforce.common.sfdx.WorkspaceConfig

import scala.collection.mutable

/** Metadata workspace, maintains information on available metadata within a project/package.
  *
  * Duplicate detection is based on the relevant MetadataDocumentType(s) being able to generate an accurate TypeName
  * for the metadata. Where multiple metadata items may contribute to a type, e.g. labels, make sure that
  * duplicatesAllowed is set which will bypass the duplicate detection. Duplicates are reported as errors and then
  * ignored.
  *
  * During an upsert/deletion of new types the index will also need to be updated so that it maintains an accurate
  * view of the metadata files being used.
  */
class Workspace(config: WorkspaceConfig) {

  /** Issues detected in workspace, typically duplicate types */
  private val logger = new CatchingLogger()

  /** All documents partitioned by declared extension */
  private val documents =
    new mutable.HashMap[Name, mutable.HashMap[TypeName, List[MetadataDocument]]]()

  /** The typeNames that may be exclusively generated by the documents, for duplicate detection */
  private val typeNames = new mutable.HashSet[TypeName]()

  index()

  /** Issues found in workspace */
  val issues: List[Issue] = logger.issues

  /** Number of types found*/
  val typeCount: Int = documents.values.map(_.size).sum

  /** Get index'd metadata by declared extension */
  def getByExtension(ext: Name): Set[MetadataDocument] = {
    if (!documents.contains(ext)) return Set.empty
    documents(ext).values.flatten.toSet
  }

  /** Get index'd metadata by declared extension */
  def getByExtensionIterable(ext: Name): Iterable[MetadataDocument] = {
    if (!documents.contains(ext)) return Set.empty
    documents(ext).values.flatten
  }

  /* Find a class or trigger by its typename */
  def getByType(typeName: TypeName): Option[MetadataDocument] = {
    documents
      .get(MetadataDocument.clsExt)
      .flatMap(_.get(typeName))
      .orElse(documents.get(MetadataDocument.triggerExt).flatMap(_.get(typeName)))
      .map(_.head)
  }

  /** Upsert a metadata document with duplicate detection */
  def upsert(metadata: MetadataDocument): Boolean = {
    // Duplicates always good
    if (metadata.duplicatesAllowed) {
      addDocument(metadata)
      return true
    }

    // Label replacement OK
    val typeName = metadata.typeName(config.namespace)
    if (typeName == TypeNames.Label)
      return true

    // New is OK
    if (!typeNames.contains(typeName)) {
      addDocument(metadata)
      return true
    }

    // Existing with same path OK, but beware some files may have been deleted without notification
    val knownDocs = documents
      .get(metadata.extension)
      .flatMap(_.get(typeName))
      .getOrElse(Nil)
    val docs = knownDocs.filter(_.path.exists)
    if (docs.size != knownDocs.size)
      documents(metadata.extension).put(typeName, docs)

    if (docs.isEmpty || docs.contains(metadata)) {
      return true
    }

    docs.foreach(doc => {
      logger.log(
        Issue(doc.path.toString,
              Diagnostic(
                ERROR_CATEGORY,
                Location(0),
                s"Duplicate type '$typeName' found in '${metadata.path}', ignoring this file")))
    })
    false
  }

  /** Remove a metadata document from the index */
  def remove(metadataDocumentType: MetadataDocument): Unit = {
    documents
      .get(metadataDocumentType.extension)
      .foreach(docs => {
        val typeName = metadataDocumentType.typeName(config.namespace)
        if (!metadataDocumentType.duplicatesAllowed) {
          docs.remove(typeName)
          typeNames.remove(typeName)
        } else {
          val filtered =
            docs.getOrElse(typeName, Nil).filterNot(_.path == metadataDocumentType.path)
          docs.put(typeName, filtered)
          typeNames.remove(typeName)
        }
      })
  }

  private def index(): Unit = {
    LoggerOps.debugTime("Indexed Project") {
      config.paths.reverse.filter(_.isDirectory).foreach(p => indexPath(p, config.forceIgnore))
      createGhostSObjectFiles(Name("field"), config.forceIgnore)
      createGhostSObjectFiles(Name("fieldSet"), config.forceIgnore)
    }
  }

  private def indexPath(path: PathLike, forceIgnore: Option[ForceIgnore]): Unit = {
    if (Workspace.isExcluded(path))
      return

    if (path.isDirectory) {
      if (forceIgnore.forall(_.includeDirectory(path))) {
        path.directoryList() match {
          case Left(err)    => LoggerOps.error(err)
          case Right(parts) => parts.foreach(part => indexPath(path.join(part), forceIgnore))
        }
      } else {
        LoggerOps.debug(LoggerOps.Trace, s"Ignoring directory $path")
      }
    } else {
      // Not testing if this is a regular file to improve scan performance, will fail later on read
      if (forceIgnore.forall(_.includeFile(path))) {
        val dt = MetadataDocument(path)
        dt.foreach(insertDocument)
      } else {
        LoggerOps.debug(LoggerOps.Trace, s"Ignoring file $path")
      }
    }
  }

  private def insertDocument(documentType: MetadataDocument): Unit = {
    if (documentType.ignorable)
      return

    if (documentType.duplicatesAllowed) {
      addDocument(documentType)
    } else {
      // Duplicate detect based on type that will be generated
      val typeName = documentType.typeName(config.namespace)
      if (typeNames.contains(typeName)) {
        val duplicate = documents(documentType.extension).get(typeName)
        logger.log(
          Issue(documentType.path.toString,
                Diagnostic(
                  ERROR_CATEGORY,
                  Location(0),
                  s"File creates duplicate type '$typeName' as '${duplicate.get.head.path}', ignoring")))
      } else {
        typeNames.add(typeName)
        addDocument(documentType)
      }
    }
  }

  private def addDocument(docType: MetadataDocument): Unit = {
    val extMap = documents.getOrElseUpdate(docType.extension, {
      mutable.HashMap[TypeName, List[MetadataDocument]]()
    })
    val typeName = docType.typeName(config.namespace)
    extMap.put(typeName, docType :: extMap.getOrElse(typeName, Nil))
  }

  /** Hack to deal with missing .object-meta.xml files in SFDX */
  private def createGhostSObjectFiles(name: Name, forceIgnore: Option[ForceIgnore]): Unit = {
    getByExtension(name).foreach(docType => {
      val objectDir = docType.path.parent.parent
      val metaFile = objectDir.join(objectDir.basename + ".object-meta.xml")
      if (!metaFile.isFile) {
        if (forceIgnore.forall(_.includeDirectory(metaFile.parent))) {
          val objectExt = MetadataDocument.objectExt
          val docType = SObjectDocument(metaFile, Name(objectDir.basename))
          if (!documents.contains(objectExt) || !documents(objectExt).contains(
                docType.typeName(config.namespace))) {
            addDocument(docType)
          }
        }
      }
    })
  }
}

object Workspace {

  /** Exclude some paths that we would waste time searching */
  def isExcluded(path: PathLike): Boolean = {
    val basename = path.basename
    if (basename.startsWith(".")) return true
    if (basename == "node_modules") return true
    false
  }
}
