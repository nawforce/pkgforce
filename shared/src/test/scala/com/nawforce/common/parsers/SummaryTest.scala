/*
 [The "BSD licence"]
 Copyright (c) 2017 Kevin Jones
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
package com.nawforce.common.parsers

import com.nawforce.common.api.{Location, Name}
import com.nawforce.common.modifiers.{PRIVATE_MODIFIER, PUBLIC_MODIFIER, STATIC_MODIFIER}
import com.nawforce.common.path.PathFactory
import com.nawforce.runtime.parsers.{CodeParser, SourceData}
import org.scalatest.funsuite.AnyFunSuite

class SummaryTest extends AnyFunSuite {

  test("Class summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public class Dummy {}"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexClassType)
        assert(root.range == Location(1, 0, 1, 21))
        assert(root.id == IdAndRange(Name("Dummy"), Location(1, 13, 1, 18)))
        assert(root.children.isEmpty)
        assert(root.modifiers.modifiers sameElements Array(PUBLIC_MODIFIER))
        assert(root.modifiers.issues.isEmpty)
        assert(root.description == "public class Dummy")
    }
  }

  test("Interface summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public interface Dummy {}"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexInterfaceType)
        assert(root.range == Location(1, 0, 1, 25))
        assert(root.id == IdAndRange(Name("Dummy"), Location(1, 17, 1, 22)))
        assert(root.children.isEmpty)
        assert(root.modifiers.modifiers sameElements Array(PUBLIC_MODIFIER))
        assert(root.modifiers.issues.isEmpty)
        assert(root.description == "public interface Dummy")
    }
  }

  test("Enum summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public enum Dummy {}"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexEnumType)
        assert(root.range == Location(1, 0, 1, 20))
        assert(root.id == IdAndRange(Name("Dummy"), Location(1, 12, 1, 17)))
        assert(root.children.isEmpty)
        assert(root.modifiers.modifiers sameElements Array(PUBLIC_MODIFIER))
        assert(root.modifiers.issues.isEmpty)
        assert(root.description == "public enum Dummy")
    }
  }

  test("Class with constructor summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(
      path,
      SourceData("public class Dummy { private Dummy(final String bar, Integer foo) {} }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexClassType)
        assert(root.children.size == 1)

        val ctor = root.children.head
        assert(ctor.nature == ApexConstructorType)
        assert(ctor.range == Location(1, 21, 1, 68))
        assert(ctor.id == IdAndRange(Name("Dummy"), Location(1, 29, 1, 34)))
        assert(ctor.children.isEmpty)
        assert(ctor.modifiers.modifiers sameElements Array(PRIVATE_MODIFIER))
        assert(ctor.modifiers.issues.isEmpty)
        assert(ctor.description == "private Dummy(final String bar, Integer foo)")

    }
  }

  test("Class with method summary") {
    val path = PathFactory("Dummy.cls")
    val cp =
      CodeParser(path,
                 SourceData("public class Dummy { private Static void Foo(final String bar) {} }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexClassType)
        assert(root.children.size == 1)

        val method = root.children.head
        assert(method.nature == ApexMethodType)
        assert(method.range == Location(1, 21, 1, 65))
        assert(method.id == IdAndRange(Name("Foo"), Location(1, 41, 1, 44)))
        assert(method.children.isEmpty)
        assert(method.modifiers.modifiers sameElements Array(PRIVATE_MODIFIER, STATIC_MODIFIER))
        assert(method.modifiers.issues.isEmpty)
        assert(method.description == "private static void Foo(final String bar)")

    }
  }

  test("Class with field summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public class Dummy { private Static Integer Foo; }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexClassType)
        assert(root.children.size == 1)

        val field = root.children.head
        assert(field.nature == ApexFieldType)
        assert(field.range == Location(1, 21, 1, 48))
        assert(field.id == IdAndRange(Name("Foo"), Location(1, 44, 1, 47)))
        assert(field.children.isEmpty)
        assert(field.modifiers.modifiers sameElements Array(PRIVATE_MODIFIER, STATIC_MODIFIER))
        assert(field.modifiers.issues.isEmpty)
        assert(field.description == "private static Integer Foo")

    }
  }

  test("Class with two fields declarators summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public class Dummy { private Static Integer Foo, bar; }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexClassType)
        assert(root.children.size == 2)

        val fooField = root.children.head
        assert(fooField.range == Location(1, 44, 1, 47))

        fooField.id == IdAndRange(Name("Foo"), Location(1, 44, 1, 47))

        val barField = root.children(1)
        assert(barField.range == Location(1, 49, 1, 52))
        assert(barField.id == IdAndRange(Name("bar"), Location(1, 49, 1, 52)))
    }
  }

  test("Class with property summary") {
    val path = PathFactory("Dummy.cls")
    val cp =
      CodeParser(path, SourceData("public class Dummy { private Static Integer Foo{get; set;} }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexClassType)
        assert(root.children.size == 1)

        val field = root.children.head
        assert(field.nature == ApexPropertyType)
        assert(field.range == Location(1, 21, 1, 58))
        assert(field.id == IdAndRange(Name("Foo"), Location(1, 44, 1, 47)))
        assert(field.children.isEmpty)
        assert(field.modifiers.modifiers sameElements Array(PRIVATE_MODIFIER, STATIC_MODIFIER))
        assert(field.modifiers.issues.isEmpty)
        assert(field.description == "private static Integer Foo")
    }
  }

  test("Interface with method summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public interface Dummy { void Foo(final String bar); }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexInterfaceType)
        assert(root.children.size == 1)

        val method = root.children.head
        assert(method.nature == ApexMethodType)
        assert(method.range == Location(1, 25, 1, 52))
        assert(method.id == IdAndRange(Name("Foo"), Location(1, 30, 1, 33)))
        assert(method.children.isEmpty)
        assert(method.modifiers.modifiers.isEmpty)
        assert(method.modifiers.issues.isEmpty)
        assert(method.description == "void Foo(final String bar)")

    }
  }

  test("Enum with constant summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public enum Dummy { BaR }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexEnumType)
        assert(root.children.size == 1)

        val constant = root.children.head
        assert(constant.nature == ApexEnumConstantType)
        assert(constant.range == Location(1, 20, 1, 23))
        assert(constant.id == IdAndRange(Name("BaR"), Location(1, 20, 1, 23)))

        assert(constant.children.isEmpty)
        assert(constant.modifiers.modifiers.isEmpty)
        assert(constant.modifiers.issues.isEmpty)
        assert(constant.description == "BaR")
    }
  }

  test("Nested class summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public class Dummy { private class Inner { } }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexClassType)
        assert(root.children.size == 1)

        val inner = root.children.head
        assert(inner.nature == ApexClassType)
    }
  }

  test("Nested interface summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public class Dummy { private interface Inner { } }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexClassType)
        assert(root.children.size == 1)

        val inner = root.children.head
        assert(inner.nature == ApexInterfaceType)
    }
  }

  test("Nested enum summary") {
    val path = PathFactory("Dummy.cls")
    val cp = CodeParser(path, SourceData("public class Dummy { private enum Inner { } }"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        assert(root.nature == ApexClassType)
        assert(root.children.size == 1)

        val inner = root.children.head
        assert(inner.nature == ApexEnumType)
    }
  }

  test("Global field in public class") {
    val path = PathFactory("Dummy.cls")
    val cp =
      CodeParser(path, SourceData("public class Dummy {global String a;}"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        val issues = root.collectIssues()

        assert(issues.length == 1)
        assert(issues.head.diagnostic.location.displayPosition == "line 1 at 34-35")
        assert(issues.head.diagnostic.message == "Enclosing class must be declared global to use global or webservice modifiers")
    }
  }

  test("Global inner interface in public class") {
    val path = PathFactory("Dummy.cls")
    val cp =
      CodeParser(path, SourceData("public class Dummy {global interface Inside {}}"))
    cp.parseClass() match {
      case Left(err) => assert(false, err)
      case Right(cu) =>
        val root = ApexNode(cp, cu)
        val issues = root.collectIssues()

        assert(issues.length == 1)
        assert(issues.head.diagnostic.location.displayPosition == "line 1 at 37-43")
        assert(issues.head.diagnostic.message == "Enclosing class must be declared global to use global or webservice modifiers")
    }
  }


}
