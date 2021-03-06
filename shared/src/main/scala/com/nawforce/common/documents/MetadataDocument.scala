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

import com.nawforce.common.api.{Name, TypeName}
import com.nawforce.common.names.{TypeNames, _}
import com.nawforce.common.path.PathLike

import scala.collection.immutable.ArraySeq.ofRef

/** A piece of Metadata described in a file */
abstract class MetadataDocument(val path: PathLike, val name: Name) {
  /* MDAPI extension for this metadata, may not match actual extension for SFDX */
  val extension: Name

  /** Set true to avoid indexing bad metadata such as empty files */
  val ignorable: Boolean = false

  /** Set true if typeName() may not be unique for different Metadata */
  val duplicatesAllowed: Boolean = false

  /** Generate a typename for this metadata, if this is not unique you may need to set duplicatesAllowed. */
  def typeName(namespace: Option[Name]): TypeName
}

abstract class UpdatableMetadata(_path: PathLike, _name: Name)
    extends MetadataDocument(_path, _name)

final case class LabelsDocument(_path: PathLike, _name: Name)
    extends UpdatableMetadata(_path, _name) {
  override val extension: Name = MetadataDocument.labelsExt
  override val duplicatesAllowed: Boolean = true
  override def typeName(namespace: Option[Name]): TypeName = TypeNames.Label
}

abstract class ApexDocument(_path: PathLike, _name: Name) extends UpdatableMetadata(_path, _name)

final case class ApexClassDocument(_path: PathLike, _name: Name)
    extends ApexDocument(_path, _name) {
  override val extension: Name = MetadataDocument.clsExt
  override def typeName(namespace: Option[Name]): TypeName = {
    TypeName(name).withNamespace(namespace)
  }
}

final case class ApexTriggerDocument(_path: PathLike, _name: Name)
    extends ApexDocument(_path, _name) {
  override val extension: Name = MetadataDocument.triggerExt
  override def typeName(namespace: Option[Name]): TypeName = {
    val qname: String = namespace
      .map(ns => s"__sfdc_trigger/${ns.value}/${name.value}")
      .getOrElse(s"__sfdc_trigger/${name.value}")
    TypeName(Name(qname))
  }
}

final case class ComponentDocument(_path: PathLike, _name: Name)
    extends UpdatableMetadata(_path, _name) {
  override val extension: Name = MetadataDocument.componentExt
  override def typeName(namespace: Option[Name]): TypeName = {
    namespace
      .map(ns => TypeName(name, Nil, Some(TypeName(ns, Nil, Some(TypeNames.Component)))))
      .getOrElse(TypeName(name, Nil, Some(TypeNames.Component)))
  }
}

abstract class SObjectLike(_path: PathLike, _name: Name) extends MetadataDocument(_path, _name)

final case class SObjectDocument(_path: PathLike, _name: Name) extends SObjectLike(_path, _name) {

  private val isCustom = name.value.endsWith("__c")
  override val extension: Name = MetadataDocument.objectExt
  override val ignorable: Boolean = path.size == 0
  override def typeName(namespace: Option[Name]): TypeName = {
    val prefix =
      if (isCustom)
        namespace.map(ns => s"${ns}__").getOrElse("")
      else
        ""
    TypeName(Name(prefix + name), Nil, Some(TypeNames.Schema))
  }
}

final case class SObjectFieldDocument(_path: PathLike, _name: Name)
    extends MetadataDocument(_path, _name) {
  override val extension: Name = MetadataDocument.fieldExt
  override def typeName(namespace: Option[Name]): TypeName = {
    val sobjectName = path.parent.parent.basename
    val prefix = namespace.map(ns => s"${ns}__").getOrElse("")
    val fieldsType = TypeNames.sObjectTypeFields$(
      TypeName(Name(prefix + sobjectName), Nil, Some(TypeNames.Schema)))
    TypeName(name, Nil, Some(fieldsType))
  }
}

final case class SObjectFieldSetDocument(_path: PathLike, _name: Name)
    extends MetadataDocument(_path, _name) {
  override val extension: Name = MetadataDocument.fieldSetExt
  override def typeName(namespace: Option[Name]): TypeName = {
    val sobjectName = path.parent.parent.basename
    val prefix = namespace.map(ns => s"${ns}__").getOrElse("")
    val fieldSetType = TypeNames.sObjectTypeFieldSets$(
      TypeName(Name(prefix + sobjectName), Nil, Some(TypeNames.Schema)))
    TypeName(name, Nil, Some(fieldSetType))
  }
}

final case class CustomMetadataDocument(_path: PathLike, _name: Name)
    extends SObjectLike(_path, _name) {
  override val extension: Name = MetadataDocument.objectExt
  override def typeName(namespace: Option[Name]): TypeName = {
    val prefix = namespace.map(ns => s"${ns}__").getOrElse("")
    TypeName(Name(prefix + name + "__mdt"), Nil, Some(TypeNames.Schema))
  }
}

final case class PlatformEventDocument(_path: PathLike, _name: Name)
    extends SObjectLike(_path, _name) {
  override val extension: Name = MetadataDocument.objectExt
  override def typeName(namespace: Option[Name]): TypeName = {
    val prefix = namespace.map(ns => s"${ns}__").getOrElse("")
    TypeName(Name(prefix + name + "__e"), Nil, Some(TypeNames.Schema))
  }
}

final case class PageDocument(_path: PathLike, _name: Name)
    extends UpdatableMetadata(_path, _name) {
  override val extension: Name = MetadataDocument.pageExt
  override def typeName(namespace: Option[Name]): TypeName = {
    val prefix = namespace.map(ns => s"${ns}__").getOrElse("")
    TypeName(Name(prefix + name), Nil, Some(TypeNames.Page))
  }
}

final case class FlowDocument(_path: PathLike, _name: Name)
    extends UpdatableMetadata(_path, _name) {
  override lazy val extension: Name = MetadataDocument.flowExt
  override def typeName(namespace: Option[Name]): TypeName = {
    namespace
      .map(ns => TypeName(name, Nil, Some(TypeName(ns, Nil, Some(TypeNames.Interview)))))
      .getOrElse(TypeName(name, Nil, Some(TypeNames.Interview)))
  }
}

object MetadataDocument {
  lazy val labelsExt: Name = Name("labels")
  lazy val clsExt: Name = Name("cls")
  lazy val triggerExt: Name = Name("trigger")
  lazy val componentExt: Name = Name("component")
  lazy val pageExt: Name = Name("page")
  lazy val flowExt: Name = Name("flow")
  lazy val objectExt: Name = Name("object")
  lazy val fieldExt: Name = Name("field")
  lazy val fieldSetExt: Name = Name("fieldSet")

  def apply(path: PathLike): Option[MetadataDocument] = {
    splitFilename(path) match {
      case Seq(name, Name("cls")) =>
        Some(ApexClassDocument(path, name))

      case Seq(name, Name("trigger")) =>
        Some(ApexTriggerDocument(path, name))

      case Seq(name, Name("component")) =>
        Some(ComponentDocument(path, name))

      case Seq(name, Name("object")) if name.value.endsWith("__mdt") =>
        Some(CustomMetadataDocument(path, name))
      case Seq(name, Name("object-meta"), Name("xml")) if name.value.endsWith("__mdt") =>
        Some(CustomMetadataDocument(path, name))

      case Seq(name, Name("object")) if name.value.endsWith("__e") =>
        Some(PlatformEventDocument(path, name))
      case Seq(name, Name("object-meta"), Name("xml")) if name.value.endsWith("__e") =>
        Some(PlatformEventDocument(path, name))

      case Seq(name, Name("object")) =>
        Some(SObjectDocument(path, name))
      case Seq(name, Name("object-meta"), Name("xml")) =>
        Some(SObjectDocument(path, name))

      case Seq(name, Name("field-meta"), Name("xml"))
          if path.parent.basename.equalsIgnoreCase("fields") && !path.parent.parent.isRoot =>
        Some(SObjectFieldDocument(path, name))

      case Seq(name, Name("fieldset-meta"), Name("xml"))
          if path.parent.basename.equalsIgnoreCase("fieldSets") && !path.parent.parent.isRoot =>
        Some(SObjectFieldSetDocument(path, name))

      case Seq(name, Name("flow")) =>
        Some(FlowDocument(path, name))
      case Seq(name, Name("flow-meta"), Name("xml")) =>
        Some(FlowDocument(path, name))

      case Seq(name, Name("labels")) =>
        Some(LabelsDocument(path, name))
      case Seq(name, Name("labels-meta"), Name("xml")) =>
        Some(LabelsDocument(path, name))

      case Seq(name, Name("page")) =>
        Some(PageDocument(path, name))
      case _ => None
    }
  }

  private def splitFilename(path: PathLike): Seq[Name] = {
    var parts = path.basename.split('.')
    if (parts.length > 3) {
      parts = List(parts.slice(0, parts.length - 2).mkString("."),
                   parts(parts.length - 2).toLowerCase(),
                   parts(parts.length - 1).toLowerCase).toArray
    } else if (parts.length == 3) {
      parts = List(parts(0), parts(1).toLowerCase, parts(2).toLowerCase).toArray
    } else if (parts.length == 2) {
      parts = List(parts(0), parts(1).toLowerCase).toArray
    }
    new ofRef(parts.map(Name(_)))
  }
}
