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

import com.nawforce.common.api.{ERROR_CATEGORY, Name}
import com.nawforce.common.path.PathLike
import com.nawforce.common.sfdx.WorkspaceConfig
import com.nawforce.runtime.FileSystemHelper
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

class WorkspaceTest extends AnyFunSuite with BeforeAndAfter {

  case class TestConfig(namespace: Option[Name], paths: Seq[PathLike]) extends WorkspaceConfig {
    override val ignorePath: Option[PathLike] = None
    override lazy val rootPaths: Seq[PathLike] = paths
  }

  test("bad dir has no files") {
    FileSystemHelper.run(Map[String, String]()) { root: PathLike =>
      val index = new Workspace(TestConfig(None, Seq(root.join("foo"))))
      assert(index.typeCount == 0)
    }
  }

  test("empty dir has no files") {
    FileSystemHelper.run(Map[String, String]()) { root: PathLike =>
      val index = new Workspace(TestConfig(None, Seq(root)))
      assert(index.typeCount == 0)
    }
  }

  test("dot dir is ignored") {
    FileSystemHelper.run(Map[String, String](".pkg/Foo.cls" -> "")) { root: PathLike =>
      val index = new Workspace(TestConfig(None, Seq(root)))
      assert(index.typeCount == 0)
    }
  }

  test("node_modules dir is ignored") {
    FileSystemHelper.run(Map[String, String]("node_modules/Foo.cls" -> "")) { root: PathLike =>
      val index = new Workspace(TestConfig(None, Seq(root)))
      assert(index.typeCount == 0)
    }
  }

  test("class file found") {
    FileSystemHelper.run(Map[String, String]("pkg/Foo.cls" -> "public class Foo {}")) {
      root: PathLike =>
        val index = new Workspace(TestConfig(None, Seq(root.join("pkg"))))
        assert(index.getByExtension(MetadataDocument.clsExt).size == 1)
        assert(
          index.getByExtension(Name("cls")) ==
            Set(ApexClassDocument(root.join("pkg").join("Foo.cls"), Name("Foo"))))
    }
  }

  test("nested class file found") {
    FileSystemHelper.run(Map[String, String]("pkg/foo/Foo.cls" -> "public class Foo {}")) {
      root: PathLike =>
        val index = new Workspace(TestConfig(None, Seq(root.join("pkg"))))
        assert(index.getByExtension(MetadataDocument.clsExt).size == 1)
        assert(
          index.getByExtension(Name("cls")) ==
            Set(ApexClassDocument(root.join("pkg").join("foo").join("Foo.cls"), Name("Foo"))))
    }
  }

  test("multiple classes found") {
    FileSystemHelper.run(
      Map[String, String]("pkg/Foo.cls" -> "public class Foo {}",
                          "/pkg/bar/Bar.cls" -> "public class Bar {}")) { root: PathLike =>
      val index = new Workspace(TestConfig(None, Seq(root.join("pkg"))))
      assert(
        index.getByExtension(Name("cls")).map(_.toString()) == Set(
          ApexClassDocument(root.join("pkg").join("Foo.cls"), Name("Foo")).toString,
          ApexClassDocument(root.join("pkg").join("bar").join("Bar.cls"), Name("Bar")).toString))
    }
  }

  test("duplicate classes error") {
    FileSystemHelper.run(
      Map[String, String]("pkg/foo/Foo.cls" -> "public class Foo {}",
                          "/pkg/bar/Foo.cls" -> "public class Foo {}")) { root: PathLike =>
      val index = new Workspace(TestConfig(None, Seq(root.join("pkg"))))
      assert(index.getByExtension(MetadataDocument.clsExt).size == 1)
      assert(index.issues.head.diagnostic.category == ERROR_CATEGORY)
      assert(index.issues.head.diagnostic.message.contains("File creates duplicate type 'Foo'"))
    }
  }

  test("duplicate labels no error") {
    FileSystemHelper.run(
      Map[String, String](
        "pkg/foo/CustomLabels.labels" -> "<CustomLabels xmlns=\"http://soap.sforce.com/2006/04/metadata\"/>",
        "/pkg/bar/CustomLabels.labels" -> "<CustomLabels xmlns=\"http://soap.sforce.com/2006/04/metadata\"/>")) {
      root: PathLike =>
        val index = new Workspace(TestConfig(None, Seq(root.join("pkg"))))
        assert(index.getByExtension(Name("labels")).size == 2)
        assert(index.issues.isEmpty)
    }
  }

  test("object file found") {
    FileSystemHelper.run(Map[String, String](
      "pkg/Foo.object" -> "<CustomObject xmlns=\\\"http://soap.sforce.com/2006/04/metadata\\\"/>")) {
      root: PathLike =>
        val index = new Workspace(TestConfig(None, Seq(root.join("pkg"))))
        assert(
          index.getByExtension(Name("object")) ==
            Set(SObjectDocument(root.join("pkg").join("Foo.object"), Name("Foo"))))
    }
  }

  test("object file found (sfdx)") {
    FileSystemHelper.run(Map[String, String](
      "pkg/Foo.object-meta.xml" -> "<CustomObject xmlns=\\\"http://soap.sforce.com/2006/04/metadata\\\"/>")) {
      root: PathLike =>
        val index = new Workspace(TestConfig(None, Seq(root.join("pkg"))))
        assert(
          index.getByExtension(Name("object")) ==
            Set(SObjectDocument(root.join("pkg").join("Foo.object-meta.xml"), Name("Foo"))))
    }
  }

  test("sfdx field ghosts object") {
    FileSystemHelper.run(Map[String, String](
      "pkg/Foo/fields/Bar.field-meta.xml" -> "<CustomField xmlns=\\\"http://soap.sforce.com/2006/04/metadata\\\"/>")) {
      root: PathLike =>
        val index = new Workspace(TestConfig(None, Seq(root.join("pkg"))))
        assert(
          index.getByExtension(Name("field")) ==
            Set(
              SObjectFieldDocument(
                root.join("pkg").join("Foo").join("fields").join("Bar.field-meta.xml"),
                Name("Bar"))))
        assert(
          index.getByExtension(Name("object")) ==
            Set(SObjectDocument(root.join("pkg").join("Foo").join("Foo.object-meta.xml"),
                                Name("Foo"))))
    }
  }

  test("sfdx fieldset ghosts object") {
    FileSystemHelper.run(Map[String, String](
      "pkg/Foo/fieldSets/Bar.fieldset-meta.xml" -> "<FieldSet xmlns=\\\"http://soap.sforce.com/2006/04/metadata\\\"/>")) {
      root: PathLike =>
        val index = new Workspace(TestConfig(None, Seq(root.join("pkg"))))
        assert(
          index.getByExtension(Name("fieldset")) ==
            Set(
              SObjectFieldSetDocument(
                root.join("pkg").join("Foo").join("fieldSets").join("Bar.fieldset-meta.xml"),
                Name("Bar"))))
        assert(
          index.getByExtension(Name("object")) ==
            Set(SObjectDocument(root.join("pkg").join("Foo").join("Foo.object-meta.xml"),
                                Name("Foo"))))
    }
  }
}
