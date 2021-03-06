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
package com.nawforce.common.sfdx

import com.nawforce.common.api.Name
import com.nawforce.common.names._
import com.nawforce.common.path.PathLike
import ujson.Value

class DependentPackage(projectPath: PathLike, config: Value.Value) {
  val namespace: Name =
    try {
      val ns = config("namespace") match {
        case ujson.Str(value) => Name(value)
        case _                => throw new SFDXProjectError("'namespace' should be a string")
      }
      if (ns.value.isEmpty)
        throw new SFDXProjectError("'namespace' can not be empty")
      else {
        ns.isLegalIdentifier match {
          case None        => ns
          case Some(error) => throw new SFDXProjectError(s"namespace '$ns' is not valid, $error")
        }
      }
    } catch {
      case _: NoSuchElementException =>
        throw new SFDXProjectError("'namespace' is required for each entry in 'dependencies'")
    }

  val path: Option[PathLike] = {
    try {
      config("path") match {
        case ujson.Str(value) =>
          Some(projectPath.join(value))
        case _ =>
          throw new SFDXProjectError("'path' should be a string")
      }
    } catch {
      case _: NoSuchElementException => None
    }
  }
}
