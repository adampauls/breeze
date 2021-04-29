package breeze.linalg.operators

import breeze.generic._
import breeze.linalg._
import breeze.linalg.support._
import breeze.macros.expand
import breeze.math.{Complex, Field, Ring, Semiring}
import breeze.util.ArrayUtil
import com.github.fommil.netlib.BLAS.{getInstance => blas}
import scalaxy.debug._
import breeze.macros._
import breeze.math.PowImplicits._


import scala.math.BigInt
import scala.reflect.ClassTag

trait DenseVectorOps extends VectorOps with DenseVector_GenericOps with DenseVector_ComparisonOps with DenseVector_SpecialOps with DenseVectorExtraSpecialOps

trait DenseVector_Vector_ExpandOps extends VectorOps with DenseVector_TraversalOps {

  @expand
  @expand.valify
  implicit def canDot_DV_V[@expand.args(Int, Double, Float, Long) T](
                                                                      implicit @expand.sequence[T](0, 0.0, 0.0f, 0L) zero: T)
  : breeze.linalg.operators.OpMulInner.Impl2[DenseVector[T], Vector[T], T] = {
    new breeze.linalg.operators.OpMulInner.Impl2[DenseVector[T], Vector[T], T] {
      def apply(a: DenseVector[T], b: Vector[T]) = {
        require(b.length == a.length, "Vectors must be the same length!")

        val ad = a.data
        var aoff = a.offset
        var result: T = zero

        var i = 0
        while (i < a.length) {
          result += ad(aoff) * b(i)
          aoff += a.stride
          i += 1
        }
        result

      }
      implicitly[BinaryRegistry[Vector[T], Vector[T], OpMulInner.type, T]].register(this)
    }
  }

  @expand
  @expand.valify
  implicit def dv_v_Op[
    @expand.args(Int, Double, Float, Long) T,
    @expand.args(OpAdd, OpSub, OpMulScalar, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
                                                                                       implicit @expand.sequence[Op]({ _ + _ }, { _ - _ }, { _ * _ }, { _ / _ }, {  (__x, __y) => __y }, { _ % _ }, { _.pow(_) })
  op: Op.Impl2[T, T, T]): BinaryRegistry[DenseVector[T], Vector[T], Op.type, DenseVector[T]] =
    new BinaryRegistry[DenseVector[T], Vector[T], Op.type, DenseVector[T]] {

      override protected def bindingMissing(a: DenseVector[T], b: Vector[T]): DenseVector[T] = {
        val ad = a.data
        var aoff = a.offset
        val result = DenseVector.zeros[T](a.length)
        val rd = result.data

        var i = 0
        while (i < a.length) {
          rd(i) = op(ad(aoff), b(i))
          aoff += a.stride
          i += 1
        }
        result
      }
      implicitly[BinaryRegistry[Vector[T], Vector[T], Op.type, Vector[T]]].register(this)
    }

  @expand
  @expand.valify
  implicit def dv_v_InPlaceOp[
    @expand.args(Int, Double, Float, Long) T,
    @expand.args(OpMulScalar, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
                                                                         implicit @expand.sequence[Op]({ _ * _ }, { _ / _ }, {  (__x, __y) => __y }, { _ % _ }, { _.pow(_) })
  op: Op.Impl2[T, T, T]): BinaryUpdateRegistry[DenseVector[T], Vector[T], Op.type] =
    new BinaryUpdateRegistry[DenseVector[T], Vector[T], Op.type] {

      override protected def bindingMissing(a: DenseVector[T], b: Vector[T]): Unit = {
        val ad = a.data
        var aoff = a.offset

        var i = 0
        while (i < a.length) {
          ad(aoff) = op(ad(aoff), b(i))
          aoff += a.stride
          i += 1
        }
      }
      implicitly[BinaryUpdateRegistry[Vector[T], Vector[T], Op.type]].register(this)
    }

  @expand
  @expand.valify
  implicit def dv_v_ZeroIdempotent_InPlaceOp[
    @expand.args(Int, Double, Float, Long) T,
    @expand.args(OpAdd, OpSub) Op <: OpType](
                                              implicit @expand.sequence[Op]({ _ + _ }, { _ - _ })
  op: Op.Impl2[T, T, T]): BinaryUpdateRegistry[DenseVector[T], Vector[T], Op.type] =
    new BinaryUpdateRegistry[DenseVector[T], Vector[T], Op.type] {

      override protected def bindingMissing(a: DenseVector[T], b: Vector[T]): Unit = {
        val ad = a.data
        var aoff = a.offset

        for ((i, v) <- b.activeIterator) {
          a(i) = op(a(i), v)
        }
      }
      implicitly[BinaryUpdateRegistry[Vector[T], Vector[T], Op.type]].register(this)
    }
}

trait DenseVectorExpandOps extends VectorOps with DenseVector_Vector_ExpandOps {
  @expand
  @expand.valify
  implicit def dv_s_Op[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpAdd, OpSub, OpMulScalar, OpMulMatrix, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ }, { _ * _ }, { _ * _ }, { _ / _ }, {  (__x, __y) => __y }, { _ % _ }, { _.pow(_) })
      op: Op.Impl2[T, T, T]): Op.Impl2[DenseVector[T], T, DenseVector[T]] =
    new Op.Impl2[DenseVector[T], T, DenseVector[T]] {
      def apply(a: DenseVector[T], b: T): DenseVector[T] = {
        val ad = a.data
        var aoff = a.offset
        val result = DenseVector.zeros[T](a.length)
        val rd = result.data
        val stride = a.stride

        // https://wikis.oracle.com/display/HotSpotInternals/RangeCheckElimination
        if (stride == 1) {
          if (aoff == 0) {
            cforRange(0 until rd.length) { j =>
              rd(j) = op(ad(j), b)
            }
          } else {
            cforRange(0 until rd.length) { j =>
              rd(j) = op(ad(j + aoff), b)
            }
          }
        } else {
          var i = 0
          var j = aoff
          while (i < rd.length) {
            rd(i) = op(ad(j), b)
            i += 1
            j += stride
          }
        }

        result
      }
      implicitly[BinaryRegistry[Vector[T], T, Op.type, Vector[T]]].register(this)
    }

  @expand
  @expand.valify
  implicit def s_dv_Op[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpAdd, OpSub, OpMulScalar, OpMulMatrix, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ }, { _ * _ }, { _ * _ }, { _ / _ }, {  (__x, __y) => __y }, { _ % _ }, { _.pow(_) })
      op: Op.Impl2[T, T, T]): Op.Impl2[T, DenseVector[T], DenseVector[T]] =
    new Op.Impl2[T, DenseVector[T], DenseVector[T]] {
      def apply(a: T, b: DenseVector[T]): DenseVector[T] = {
        val bd = b.data
        var boff = b.offset
        val result = DenseVector.zeros[T](b.length)
        val rd = result.data

        var i = 0
        while (i < b.length) {
          rd(i) = op(a, bd(boff))
          boff += b.stride
          i += 1
        }
        result
      }
      implicitly[BinaryRegistry[T, Vector[T], Op.type, Vector[T]]].register(this)
    }

  @expand
  @expand.valify
  implicit def dv_dv_Op[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpAdd, OpSub, OpMulScalar, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ }, { _ * _ }, { _ / _ }, {  (__x, __y) => __y }, { _ % _ }, { _.pow(_) })
      op: Op.Impl2[T, T, T]): Op.Impl2[DenseVector[T], DenseVector[T], DenseVector[T]] = {
    new Op.Impl2[DenseVector[T], DenseVector[T], DenseVector[T]] {
      def apply(a: DenseVector[T], b: DenseVector[T]): DenseVector[T] = {
        require(a.length == b.length, "Lengths must match!")
        val ad = a.data
        val bd = b.data
        var aoff = a.offset
        var boff = b.offset
        val result = DenseVector.zeros[T](a.length)
        val rd = result.data

        if (a.noOffsetOrStride && b.noOffsetOrStride) {
          cforRange(0 until a.length) { j =>
            rd(j) = op(ad(j), bd(j))
          }
        } else if (a.stride == 1 && b.stride == 1) {
          cforRange(0 until a.length) { j =>
            rd(j) = op(ad(j + aoff), bd(j + boff))
          }
        } else {
          var i = 0
          while (i < a.length) {
            rd(i) = op(ad(aoff), bd(boff))
            aoff += a.stride
            boff += b.stride
            i += 1
          }
        }
        result
      }
      implicitly[BinaryRegistry[Vector[T], Vector[T], Op.type, Vector[T]]].register(this)
    }
  }

  @expand
  @expand.valify
  implicit def dv_dv_UpdateOp[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpAdd, OpSub, OpMulScalar, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ }, { _ * _ }, { _ / _ }, {  (__x, __y) => __y }, { _ % _ }, { _.pow(_) })
      op: Op.Impl2[T, T, T]): Op.InPlaceImpl2[DenseVector[T], DenseVector[T]] =
    new Op.InPlaceImpl2[DenseVector[T], DenseVector[T]] {
      def apply(a: DenseVector[T], b: DenseVector[T]): Unit = {
        require(a.length == b.length, "Lengths must match!")
        val ad = a.data
        val bd = b.data
        val aoff = a.offset
        val boff = b.offset
        val astride = a.stride
        val bstride = b.stride
        val length = a.length

        if (a.overlaps(b)) {
          apply(a, b.copy)
        } else if (a.noOffsetOrStride && b.noOffsetOrStride) {
          cforRange(0 until length) { j =>
            ad(j) = op(ad(j), bd(j))
          }
        } else if (astride == 1 && bstride == 1) {
          cforRange(0 until length) { j =>
            ad(j + aoff) = op(ad(j + aoff), bd(j + boff))
          }
        } else {
          cforRange(0 until length) { i =>
            ad(aoff + astride * i) = op(ad(aoff + astride * i), bd(boff + bstride * i))
          }
        }
      }
      implicitly[BinaryUpdateRegistry[Vector[T], Vector[T], Op.type]].register(this)
    }

  @expand
  @expand.valify
  implicit def dv_s_UpdateOp[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpAdd, OpSub, OpMulScalar, OpMulMatrix, OpDiv, OpSet, OpMod) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ }, { _ * _ }, { _ * _ }, { _ / _ }, {  (__x, __y) => __y }, { _ % _ })
      op: Op.Impl2[T, T, T]): Op.InPlaceImpl2[DenseVector[T], T] = new Op.InPlaceImpl2[DenseVector[T], T] {
    def apply(a: DenseVector[T], b: T): Unit = {
      val ad = a.data
      val aoff = a.offset
      val stride = a.stride
      val length = a.length

      // ABCE branching
      if (aoff == 0 && stride == 1) {
        fastPath(b, ad, length)
      } else if (stride == 1) {
        medPath(ad, aoff, b, length)
      } else {
        slowPath(ad, aoff, stride, b, length)
      }

    }

    private def fastPath(b: T, ad: Array[T], length: Int): Unit = {
      cforRange(0 until length) { j =>
        ad(j) = op(ad(j), b)
      }
    }

    private def medPath(ad: Array[T], aoff: Int, b: T, length: Int): Unit = {
      cforRange(0 until length) { j =>
        ad(j + aoff) = op(ad(j + aoff), b)
      }
    }

    private def slowPath(ad: Array[T], aoff: Int, stride: Int, b: T, length: Int): Unit = {
      var i = 0
      var j = aoff
      while (i < length) {
        ad(j) = op(ad(j), b)
        i += 1
        j += stride
      }
    }

    implicitly[BinaryUpdateRegistry[Vector[T], T, Op.type]].register(this)
  }

  @expand
  @expand.valify
  implicit def canDot_DV_DV[@expand.args(Int, Long) T](implicit @expand.sequence[T](0, 0L) zero: T)
    : breeze.linalg.operators.OpMulInner.Impl2[DenseVector[T], DenseVector[T], T] = {
    new breeze.linalg.operators.OpMulInner.Impl2[DenseVector[T], DenseVector[T], T] {
      def apply(a: DenseVector[T], b: DenseVector[T]) = {
        require(b.length == a.length, "Vectors must be the same length!")

        val ad = a.data
        val bd = b.data
        var aoff = a.offset
        var boff = b.offset
        var result: T = zero

        var i = 0
        while (i < a.length) {
          result += ad(aoff) * bd(boff)
          aoff += a.stride
          boff += b.stride
          i += 1
        }
        result

      }
      implicitly[BinaryRegistry[Vector[T], Vector[T], OpMulInner.type, T]].register(this)
    }
  }


  @expand
  @expand.valify
  implicit def canZipValues_DV_DV[@expand.args(Int, Double, Float, Long) T]()
    : zipValues.Impl2[DenseVector[T], DenseVector[T], ZippedValues[T, T]] = {
    val res = new zipValues.Impl2[DenseVector[T], DenseVector[T], ZippedValues[T, T]] {
      def apply(v1: DenseVector[T], v2: DenseVector[T]) = {
        require(v2.length == v1.length, "vector length mismatch")
        val n = v1.length
        new ZippedValues[T, T] {
          def foreach(fn: (T, T) => Unit): Unit = {
            if (v1.stride == 1 && v2.stride == 1) {
              val data1 = v1.data
              val offset1 = v1.offset
              val data2 = v2.data
              val offset2 = v2.offset
              cforRange(0 until v1.length) { i =>
                fn(data1(offset1 + i), data2(offset2 + i))
              }
            } else {
              slowPath(fn)
            }
          }

          def slowPath(fn: (T, T) => Unit): Unit = {
            val data1 = v1.data
            val stride1 = v1.stride
            var offset1 = v1.offset
            val data2 = v2.data
            val stride2 = v2.stride
            var offset2 = v2.offset
            var i = 0
            while (i < n) {
              fn(data1(offset1), data2(offset2))
              i += 1
              offset1 += stride1
              offset2 += stride2
            }
          }
        }
      }
    }

    implicitly[BinaryRegistry[Vector[T], Vector[T], zipValues.type, ZippedValues[T, T]]].register(res)

    res
  }

  @expand
  @expand.valify
  implicit def impl_scaleAdd_InPlace_DV_T_DV[@expand.args(Int, Long) V]
    : scaleAdd.InPlaceImpl3[DenseVector[V], V, DenseVector[V]] = {
    new scaleAdd.InPlaceImpl3[DenseVector[V], V, DenseVector[V]] {
      def apply(y: DenseVector[V], s: V, x: DenseVector[V]): Unit = {
        require(x.length == y.length, "Vectors must be the same length!")
        if (y.overlaps(x)) {
          apply(y, s, x.copy)
        } else if (x.noOffsetOrStride && y.noOffsetOrStride) {
          val ad = x.data
          val bd = y.data
          cforRange(0 until x.length) { i =>
            bd(i) += ad(i) * s
          }
        } else {
          cforRange(0 until x.length) { i =>
            y(i) += x(i) * s
          }
        }
      }
      implicitly[TernaryUpdateRegistry[Vector[V], V, Vector[V], scaleAdd.type]].register(this)
    }
  }

  // TODO: try removing
  implicit def dvAddIntoField[T](
      implicit field: Semiring[T],
      ct: ClassTag[T]): OpAdd.InPlaceImpl2[DenseVector[T], DenseVector[T]] = {
    new OpAdd.InPlaceImpl2[DenseVector[T], DenseVector[T]] {
      override def apply(v: DenseVector[T], v2: DenseVector[T]) = {
        if (v.overlaps(v2)) {
          apply(v, v2.copy)
        } else {
          for (i <- 0 until v.length) v(i) = field.+(v(i), v2(i))
        }
      }
    }
  }

  // TODO: try removing
  implicit def dvSubIntoField[T](
      implicit field: Ring[T],
      ct: ClassTag[T]): OpSub.InPlaceImpl2[DenseVector[T], DenseVector[T]] = {
    new OpSub.InPlaceImpl2[DenseVector[T], DenseVector[T]] {
      override def apply(v: DenseVector[T], v2: DenseVector[T]) = {
        if (v.overlaps(v2)) {
          apply(v, v2.copy)
        } else {
          for (i <- 0 until v.length) v(i) = field.-(v(i), v2(i))
        }
      }
    }

  }

  implicit def dvMulIntoField[T](
      implicit field: Semiring[T],
      ct: ClassTag[T]): OpMulScalar.InPlaceImpl2[DenseVector[T], DenseVector[T]] = {
    new OpMulScalar.InPlaceImpl2[DenseVector[T], DenseVector[T]] {
      override def apply(v: DenseVector[T], v2: DenseVector[T]) = {
        if (v.overlaps(v2)) {
          apply(v, v2.copy)
        } else {
          for (i <- 0 until v.length) v(i) = field.*(v(i), v2(i))
        }
      }
    }

  }

  implicit def dvDivIntoField[T](
      implicit field: Field[T],
      ct: ClassTag[T]): OpDiv.InPlaceImpl2[DenseVector[T], DenseVector[T]] = {
    new OpDiv.InPlaceImpl2[DenseVector[T], DenseVector[T]] {
      override def apply(v: DenseVector[T], v2: DenseVector[T]) = {
        if (v.overlaps(v2)) {
          apply(v, v2.copy)
        } else {
          for (i <- 0 until v.length) v(i) = field./(v(i), v2(i))
        }
      }
    }

  }

  implicit def dvPowInto[T](
      implicit pow: OpPow.Impl2[T, T, T],
      ct: ClassTag[T]): OpPow.InPlaceImpl2[DenseVector[T], DenseVector[T]] = {
    new OpPow.InPlaceImpl2[DenseVector[T], DenseVector[T]] {
      override def apply(v: DenseVector[T], v2: DenseVector[T]) = {
        if (v.overlaps(v2)) {
          apply(v, v2.copy)
        } else {
          for (i <- 0 until v.length) v(i) = pow(v(i), v2(i))
        }
      }
    }

  }

  implicit def dvAddIntoSField[T](
      implicit field: Semiring[T],
      ct: ClassTag[T]): OpAdd.InPlaceImpl2[DenseVector[T], T] = {
    new OpAdd.InPlaceImpl2[DenseVector[T], T] {
      override def apply(v: DenseVector[T], v2: T) = {
        for (i <- 0 until v.length) v(i) = field.+(v(i), v2)
      }
    }

  }

  implicit def dvSubIntoSField[T](implicit field: Ring[T], ct: ClassTag[T]): OpSub.InPlaceImpl2[DenseVector[T], T] = {
    new OpSub.InPlaceImpl2[DenseVector[T], T] {
      override def apply(v: DenseVector[T], v2: T) = {
        for (i <- 0 until v.length) v(i) = field.-(v(i), v2)
      }
    }

  }

  implicit def dvMulScalarIntoSField[T](
      implicit field: Semiring[T],
      ct: ClassTag[T]): OpMulScalar.InPlaceImpl2[DenseVector[T], T] = {
    new OpMulScalar.InPlaceImpl2[DenseVector[T], T] {
      override def apply(v: DenseVector[T], v2: T) = {
        for (i <- 0 until v.length) v(i) = field.*(v(i), v2)
      }
    }
  }

  implicit def dvDivIntoSField[T](implicit field: Field[T], ct: ClassTag[T]): OpDiv.InPlaceImpl2[DenseVector[T], T] = {
    new OpDiv.InPlaceImpl2[DenseVector[T], T] {
      override def apply(v: DenseVector[T], v2: T) = {
        for (i <- 0 until v.length) v(i) = field./(v(i), v2)
      }
    }
  }

  implicit def dvPowIntoS[T](
      implicit pow: OpPow.Impl2[T, T, T],
      ct: ClassTag[T]): OpPow.InPlaceImpl2[DenseVector[T], T] = {
    new OpPow.InPlaceImpl2[DenseVector[T], T] {
      override def apply(v: DenseVector[T], v2: T) = {
        for (i <- 0 until v.length) v(i) = pow(v(i), v2)
      }
    }
  }

  // todo: try removing
  implicit def DV_dotField[T](implicit field: Semiring[T]): OpMulInner.Impl2[DenseVector[T], DenseVector[T], T] = {
    new OpMulInner.Impl2[DenseVector[T], DenseVector[T], T] {
      override def apply(v: DenseVector[T], v2: DenseVector[T]): T = {
        var acc = field.zero
        for (i <- 0 until v.length) {
          acc = field.+(acc, field.*(v(i), v2(i)))
        }
        acc
      }
    }
  }

}

trait DenseVector_SpecialOps extends DenseVectorExpandOps {

  implicit val impl_OpAdd_InPlace_DV_DV_eq_DV_Float: OpAdd.InPlaceImpl2[DenseVector[Float], DenseVector[Float]] = {
    new OpAdd.InPlaceImpl2[DenseVector[Float], DenseVector[Float]] {
      def apply(a: DenseVector[Float], b: DenseVector[Float]) = {
        canSaxpy(a, 1.0f, b)
      }
      implicitly[BinaryUpdateRegistry[Vector[Float], Vector[Float], OpAdd.type]].register(this)
    }
  }

  implicit object canSaxpy
      extends scaleAdd.InPlaceImpl3[DenseVector[Float], Float, DenseVector[Float]]
      with Serializable {
    def apply(y: DenseVector[Float], a: Float, x: DenseVector[Float]): Unit = {
      require(x.length == y.length, s"Vectors must have same length")
      if (y.overlaps(x)) {
        apply(y, a, x.copy)
      } else if (x.noOffsetOrStride && y.noOffsetOrStride) {
        // using blas here is always a bad idea.
        val ad = x.data
        val bd = y.data

        cforRange(0 until x.length) { i =>
          bd(i) += ad(i) * a
        }

      } else {
        slowPath(y, a, x)
      }
    }

    private def slowPath(y: DenseVector[Float], a: Float, x: DenseVector[Float]): Unit = {
      cforRange(0 until x.length) { i =>
        y(i) += x(i) * a
      }
    }
  }
  implicitly[TernaryUpdateRegistry[Vector[Float], Float, Vector[Float], scaleAdd.type]].register(canSaxpy)

  implicit val canAddF: OpAdd.Impl2[DenseVector[Float], DenseVector[Float], DenseVector[Float]] = {
    pureFromUpdate(implicitly, implicitly)
  }
  implicitly[BinaryRegistry[Vector[Float], Vector[Float], OpAdd.type, Vector[Float]]].register(canAddF)

  implicit val canSubIntoF: OpSub.InPlaceImpl2[DenseVector[Float], DenseVector[Float]] = {
    new OpSub.InPlaceImpl2[DenseVector[Float], DenseVector[Float]] {
      def apply(a: DenseVector[Float], b: DenseVector[Float]) = {
        canSaxpy(a, -1.0f, b)
      }
      implicitly[BinaryUpdateRegistry[Vector[Float], Vector[Float], OpSub.type]].register(this)
    }

  }
  implicit val canSubF: OpSub.Impl2[DenseVector[Float], DenseVector[Float], DenseVector[Float]] = {
    pureFromUpdate(implicitly, implicitly)
  }

  implicit val canDot_DV_DV_Float
    : breeze.linalg.operators.OpMulInner.Impl2[DenseVector[Float], DenseVector[Float], Float] = {
    new breeze.linalg.operators.OpMulInner.Impl2[DenseVector[Float], DenseVector[Float], Float] {
      def apply(a: DenseVector[Float], b: DenseVector[Float]) = {
        require(a.length == b.length, s"Vectors must have same length")
        if (a.noOffsetOrStride && b.noOffsetOrStride && a.length < DenseVectorSupportMethods.MAX_SMALL_DOT_PRODUCT_LENGTH) {
          DenseVectorSupportMethods.smallDotProduct_Float(a.data, b.data, a.length)
        } else {
          blasPath(a, b)
        }
      }

      val UNROLL_FACTOR = 6

      private def blasPath(a: DenseVector[Float], b: DenseVector[Float]): Float = {
        if ((a.length <= 300 || !usingNatives) && a.stride == 1 && b.stride == 1) {
          DenseVectorSupportMethods.dotProduct_Float(a.data, a.offset, b.data, b.offset, a.length)
        } else {
          val boff = if (b.stride >= 0) b.offset else (b.offset + b.stride * (b.length - 1))
          val aoff = if (a.stride >= 0) a.offset else (a.offset + a.stride * (a.length - 1))
          blas.sdot(a.length, b.data, boff, b.stride, a.data, aoff, a.stride)
        }
      }
      implicitly[BinaryRegistry[Vector[Float], Vector[Float], OpMulInner.type, Float]].register(this)
    }
  }
}

/**
 * Operators for comparisons
 *
 * @author dlwh
 **/
trait DenseVector_ComparisonOps extends DenseVectorExpandOps {

  @expand
  implicit def impl_Op_DV_DV_eq_BV_comparison[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpGT, OpGTE, OpLTE, OpLT, OpEq, OpNe) Op <: OpType](
      implicit @expand.sequence[Op]({ _ > _ }, { _ >= _ }, { _ <= _ }, { _ < _ }, { _ == _ }, { _ != _ })
      op: Op.Impl2[T, T, T]): Op.Impl2[DenseVector[T], DenseVector[T], BitVector] =
    new Op.Impl2[DenseVector[T], DenseVector[T], BitVector] {
      def apply(a: DenseVector[T], b: DenseVector[T]): BitVector = {
        if (a.length != b.length)
          throw new ArrayIndexOutOfBoundsException(s"Lengths don't match for operator $Op ${a.length} ${b.length}")
        val ad = a.data
        val bd = b.data
        var aoff = a.offset
        var boff = b.offset
        val result = BitVector.zeros(a.length)

        var i = 0
        while (i < a.length) {
          result(i) = op(ad(aoff), bd(boff))
          aoff += a.stride
          boff += b.stride
          i += 1
        }
        result
      }
    }

  @expand
  implicit def impl_Op_DV_V_eq_BV_Comparison[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpGT, OpGTE, OpLTE, OpLT, OpEq, OpNe) Op <: OpType](
      implicit @expand.sequence[Op]({ _ > _ }, { _ >= _ }, { _ <= _ }, { _ < _ }, { _ == _ }, { _ != _ })
      op: Op.Impl2[T, T, Boolean]): Op.Impl2[DenseVector[T], Vector[T], BitVector] =
    new Op.Impl2[DenseVector[T], Vector[T], BitVector] {
      def apply(a: DenseVector[T], b: Vector[T]): BitVector = {
        require(a.length == b.length, "Vector lengths must match!")
        val ad = a.data
        var aoff = a.offset
        val result = BitVector.zeros(a.length)

        var i = 0
        while (i < a.length) {
          result(i) = op(ad(aoff), b(i))
          aoff += a.stride
          i += 1
        }
        result
      }
    }

  @expand
  implicit def impl_Op_DV_S_eq_BV_comparison[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpGT, OpGTE, OpLTE, OpLT, OpEq, OpNe) Op <: OpType](
      implicit @expand.sequence[Op]({ _ > _ }, { _ >= _ }, { _ <= _ }, { _ < _ }, { _ == _ }, { _ != _ })
      op: Op.Impl2[T, T, Boolean]): Op.Impl2[DenseVector[T], T, BitVector] =
    new Op.Impl2[DenseVector[T], T, BitVector] {
      def apply(a: DenseVector[T], b: T): BitVector = {
        val ad = a.data
        var aoff = a.offset
        val result = BitVector.zeros(a.length)

        var i = 0
        while (i < a.length) {
          result(i) = op(ad(aoff), b)
          aoff += a.stride
          i += 1
        }
        result
      }
    }

}