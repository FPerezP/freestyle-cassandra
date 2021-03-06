/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package freestyle.cassandra
package codecs

import java.nio.ByteBuffer

import cats.instances.try_._
import cats.syntax.either._
import com.datastax.driver.core.exceptions.InvalidTypeException
import com.datastax.driver.core.{DataType, ProtocolVersion, TypeCodec}
import freestyle.cassandra.codecs
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Assertion, Matchers, WordSpec}
import org.scalatest.prop.Checkers

import scala.util.{Success, Try}

class CodecsSpec extends WordSpec with Matchers with Checkers with MockFactory {

  import codecs._
  import TestUtils._

  def checkInverseCodec[T](codec: ByteBufferCodec[T])(implicit A: Arbitrary[T]): Assertion =
    check {
      forAll { v: T =>
        codec.serialize(v) match {
          case Success(result) => codec.deserialize(result) == Success(v)
          case _               => false
        }
      }
    }

  def byteBufferGen[T](codec: ByteBufferCodec[T], defaultValue: T)(
      implicit A: Arbitrary[T]): Gen[(ByteBuffer, T)] = {

    val nullByteBuffer = Null[ByteBuffer]

    def codecGen: Gen[(ByteBuffer, T)] =
      for {
        value <- A.arbitrary
        bb = codec.serialize(value).get
        remaining <- Gen.chooseNum[Int](0, bb.limit())
        _ = bb.position(bb.limit() - remaining)
      } yield (bb, value)

    Gen.oneOf(Gen.const((nullByteBuffer, defaultValue)), codecGen)
  }

  def checkDeserialize[T](codec: ByteBufferCodec[T], byteSize: Int, defaultValue: T)(
      implicit A: Arbitrary[T]): Assertion = {
    val prop = forAll(byteBufferGen(codec, defaultValue)) {
      case (bb, v) =>
        val deserialized = codec.deserialize(bb)
        val remaining    = Option(bb).map(_.remaining()).getOrElse(0)
        if (remaining == 0) {
          deserialized == Success(defaultValue)
        } else if (remaining == byteSize) {
          deserialized == Success(v)
        } else {
          deserialized.isFailure && deserialized.failed.get.isInstanceOf[InvalidTypeException]
        }
    }
    check(prop, minSuccessful(500))
  }

  abstract class MyStringTypeCodec extends TypeCodec[String](DataType.varchar(), classOf[String])

  "Boolean codec" should {

    val codec        = codecs.booleanCodec
    val byteSize     = 1
    val defaultValue = false

    "check that the serialize and deserialize are invertible" in {
      checkInverseCodec(codec)
    }

    "deserialize all possible values" in {
      checkDeserialize(codec, byteSize, defaultValue)
    }
  }

  "Byte codec" should {

    val codec        = codecs.byteCodec
    val byteSize     = 1
    val defaultValue = 0

    "check that the serialize and deserialize are invertible" in {
      checkInverseCodec(codec)
    }

    "deserialize all possible values" in {
      checkDeserialize(codec, byteSize, defaultValue.toByte)
    }
  }

  "Double codec" should {

    val codec        = codecs.doubleCodec
    val byteSize     = 8
    val defaultValue = 0d

    "check that the serialize and deserialize are invertible" in {
      checkInverseCodec(codec)
    }

    "deserialize all possible values" in {
      checkDeserialize(codec, byteSize, defaultValue)
    }
  }

  "Float codec" should {

    val codec        = codecs.floatCodec
    val byteSize     = 4
    val defaultValue = 0f

    "check that the serialize and deserialize are invertible" in {
      checkInverseCodec(codec)
    }

    "deserialize all possible values" in {
      checkDeserialize(codec, byteSize, defaultValue)
    }
  }

  "Int codec" should {

    val codec        = codecs.intCodec
    val byteSize     = 4
    val defaultValue = 0

    "check that the serialize and deserialize are invertible" in {
      checkInverseCodec(codec)
    }

    "deserialize all possible values" in {
      checkDeserialize(codec, byteSize, defaultValue)
    }
  }

  "Long codec" should {

    val codec        = codecs.longCodec
    val byteSize     = 8
    val defaultValue = 0l

    "check that the serialize and deserialize are invertible" in {
      checkInverseCodec(codec)
    }

    "deserialize all possible values" in {
      checkDeserialize(codec, byteSize, defaultValue)
    }
  }

  "Short codec" should {

    val codec        = codecs.shortCodec
    val byteSize     = 2
    val defaultValue = 0

    "check that the serialize and deserialize are invertible" in {
      checkInverseCodec(codec)
    }

    "deserialize all possible values" in {
      checkDeserialize(codec, byteSize, defaultValue.toShort)
    }
  }

  "Generic ByteBufferCodec" should {

    val bb = ByteBuffer.allocate(10)
    val pc = ProtocolVersion.V1

    "call to serialize and deserialize with the right parameters" in {

      check {
        forAll { value: String =>
          val tcMock = mock[MyStringTypeCodec]
          val codec  = codecs.byteBufferCodec(tcMock, pc)

          (tcMock.serialize _).expects(value, pc).returns(bb)
          (tcMock.deserialize _).expects(bb, pc).returns(value)
          codec.serialize(value) == Success(bb) && codec.deserialize(bb) == Success(value)
        }
      }
    }
  }

}
