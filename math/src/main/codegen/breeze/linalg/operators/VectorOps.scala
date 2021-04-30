package breeze.linalg.operators

import breeze.generic.UFunc
import breeze.generic.UFunc.{InPlaceImpl2, UImpl, UImpl2}
import breeze.gymnastics.&:&
import breeze.linalg.support.CanCopy
import breeze.linalg.{Matrix, Vector, VectorBuilder, ZippedValues, scaleAdd, zipValues}
import breeze.macros.expand
import breeze.math.{Field, Ring, Semiring}
import breeze.storage.Zero

import scala.reflect.ClassTag
import scala.util.NotGiven

trait VectorOps extends VectorExpandOps


// TODO: names
trait VectorExpandOps extends Vector_GenericOps {


  import breeze.math.PowImplicits._
  @expand.valify
  @expand
  implicit def v_v_Idempotent_Op[@expand.args(Int, Double, Float, Long) T, @expand.args(OpAdd, OpSub) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ })
      op: Op.Impl2[T, T, T]): BinaryRegistry[Vector[T], Vector[T], Op.type, Vector[T]] =
    new BinaryRegistry[Vector[T], Vector[T], Op.type, Vector[T]] {
      override def bindingMissing(a: Vector[T], b: Vector[T]): Vector[T] = {
        require(b.length == a.length, "Vectors must be the same length!")
        val result = a.copy
        for ((k, v) <- b.activeIterator) {
          result(k) = op(a(k), v)
        }
        result
      }
    }

  implicit def v_v_Idempotent_OpSub[T: Ring]: OpSub.Impl2[Vector[T], Vector[T], Vector[T]] =
    new OpSub.Impl2[Vector[T], Vector[T], Vector[T]] {
      val r = implicitly[Ring[T]]
      def apply(a: Vector[T], b: Vector[T]): Vector[T] = {
        require(b.length == a.length, "Vectors must be the same length!")
        val result = a.copy
        for ((k, v) <- b.activeIterator) {
          result(k) = r.-(a(k), v)
        }
        result
      }
    }

  implicit def v_v_Idempotent_OpAdd[T: Semiring]: OpAdd.Impl2[Vector[T], Vector[T], Vector[T]] =
    new OpAdd.Impl2[Vector[T], Vector[T], Vector[T]] {
      val r = implicitly[Semiring[T]]
      def apply(a: Vector[T], b: Vector[T]): Vector[T] = {
        require(b.length == a.length, "Vectors must be the same length!")
        val result = a.copy
        for ((k, v) <- b.activeIterator) {
          result(k) = r.+(a(k), v)
        }
        result
      }
    }

  @expand
  @expand.valify
  implicit def v_v_nilpotent_Op[@expand.args(Int, Double, Float, Long) T](
      implicit @expand.sequence[T](0, 0.0, 0.0f, 0L) zero: T)
    : BinaryRegistry[Vector[T], Vector[T], OpMulScalar.type, Vector[T]] =
    new BinaryRegistry[Vector[T], Vector[T], OpMulScalar.type, Vector[T]] {
      override def bindingMissing(a: Vector[T], b: Vector[T]): Vector[T] = {
        require(b.length == a.length, "Vectors must be the same length!")
        val builder = new VectorBuilder[T](a.length)
        for ((k, v) <- b.activeIterator) {
          val r = a(k) * v
          if (r != zero)
            builder.add(k, r)
        }
        builder.toVector
      }
    }
  @expand
  @expand.valify
  implicit def v_v_Op[@expand.args(Int, Double, Float, Long) T, @expand.args(OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
      implicit @expand.sequence[Op]({ _ / _ }, { (_x, _y) =>
        _y
      }, { _ % _ }, { _.pow(_) })
      op: Op.Impl2[T, T, T]): BinaryRegistry[Vector[T], Vector[T], Op.type, Vector[T]] =
    new BinaryRegistry[Vector[T], Vector[T], Op.type, Vector[T]] {
      override def bindingMissing(a: Vector[T], b: Vector[T]): Vector[T] = {
        require(b.length == a.length, "Vectors must be the same length!")
        val result = Vector.zeros[T](a.length)
        var i = 0
        while (i < a.length) {
          result(i) = op(a(i), b(i))
          i += 1
        }
        result
      }
    }

  /*
  @expand

  implicit def cast_v_v_Op[V1, V2,
  @expand.args(Int, Double, Float, Long) T,
  @expand.args(OpAdd, OpSub, OpMulScalar,OpDiv, OpSet, OpMod, OpPow) Op <: OpType](implicit v1: V1<:<Vector[T], v2: V2<:<Vector[T]) = {
    implicitly[BinaryRegistry[Vector[T], Vector[T], Op.type, Vector[T]]].asInstanceOf[Op.Impl2[V1, V2, Vector[T]]]
  }
   */

  @expand
  @expand.valify
  implicit def v_s_Op[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpAdd, OpSub, OpMulScalar, OpMulMatrix, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ }, { _ * _ }, { _ * _ }, { _ / _ }, { (_x, _y) =>
        _y
      }, { _ % _ }, { _.pow(_) })
      op: Op.Impl2[T, T, T],
      @expand.sequence[T](0, 0.0, 0.0f, 0L)
      zero: T): BinaryRegistry[Vector[T], T, Op.type, Vector[T]] =
    new BinaryRegistry[Vector[T], T, Op.type, Vector[T]] {
      override def bindingMissing(a: Vector[T], b: T): Vector[T] = {
        val result = Vector.zeros[T](a.length)

        var i = 0
        while (i < a.length) {
          result(i) = op(a(i), b)
          i += 1
        }
        result
      }
    }

  @expand
  @expand.valify
  implicit def s_v_Op[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpAdd, OpSub, OpMulScalar, OpMulMatrix, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ }, { _ * _ }, { _ * _ }, { _ / _ }, { (_x, _y) =>
        _y
      }, { _ % _ }, { _.pow(_) })
      op: Op.Impl2[T, T, T],
      @expand.sequence[T](0, 0.0, 0.0f, 0L) zero: T): BinaryRegistry[T, Vector[T], Op.type, Vector[T]] =
    new BinaryRegistry[T, Vector[T], Op.type, Vector[T]] {
      override def bindingMissing(b: T, a: Vector[T]): Vector[T] = {
        val result = Vector.zeros[T](a.length)

        var i = 0
        while (i < a.length) {
          result(i) = op(b, a(i))
          i += 1
        }
        result
      }
    }

  @expand
  implicit def v_sField_Op[
      @expand.args(OpAdd, OpSub, OpMulScalar, OpMulMatrix, OpDiv, OpMod, OpPow) Op <: OpType,
      T: Field: ClassTag](implicit @expand.sequence[Op]({ f.+(_, _) }, { f.-(_, _) }, { f.*(_, _) }, { f.*(_, _) }, {
    f./(_, _)
  }, { f.%(_, _) }, { f.pow(_, _) }) op: Op.Impl2[T, T, T]): BinaryRegistry[Vector[T], T, Op.type, Vector[T]] =
    new BinaryRegistry[Vector[T], T, Op.type, Vector[T]] {
      val f = implicitly[Field[T]]
      override def bindingMissing(a: Vector[T], b: T): Vector[T] = {
        val result = Vector.zeros[T](a.length)

        var i = 0
        while (i < a.length) {
          result(i) = op(a(i), b)
          i += 1
        }
        result
      }
    }
  @expand
  @expand.valify
  implicit def v_v_UpdateOp[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpMulScalar, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
      implicit @expand.sequence[Op]({ _ * _ }, { _ / _ }, { (_x, _y) =>
        _y
      }, { _ % _ }, { _.pow(_) })
      op: Op.Impl2[T, T, T]): BinaryUpdateRegistry[Vector[T], Vector[T], Op.type] =
    new BinaryUpdateRegistry[Vector[T], Vector[T], Op.type] {
      override def bindingMissing(a: Vector[T], b: Vector[T]): Unit = {
        require(b.length == a.length, "Vectors must be the same length!")
        var i = 0
        while (i < a.length) {
          a(i) = op(a(i), b(i))
          i += 1
        }
      }
    }

  @expand
  @expand.valify
  implicit def v_v_Idempotent_UpdateOp[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpAdd, OpSub) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ })
      op: Op.Impl2[T, T, T]): BinaryUpdateRegistry[Vector[T], Vector[T], Op.type] =
    new BinaryUpdateRegistry[Vector[T], Vector[T], Op.type] {
      override def bindingMissing(a: Vector[T], b: Vector[T]): Unit = {
        require(b.length == a.length, "Vectors must be the same length!")
        for ((k, v) <- b.activeIterator) {
          a(k) = op(a(k), v)
        }
      }
    }

  @expand
  @expand.valify
  implicit def v_s_UpdateOp[
      @expand.args(Int, Double, Float, Long) T,
      @expand.args(OpAdd, OpSub, OpMulScalar, OpMulMatrix, OpDiv, OpSet, OpMod, OpPow) Op <: OpType](
      implicit @expand.sequence[Op]({ _ + _ }, { _ - _ }, { _ * _ }, { _ * _ }, { _ / _ }, { (_x, _y) =>
        _y
      }, { _ % _ }, { _.pow(_) })
      op: Op.Impl2[T, T, T]): BinaryUpdateRegistry[Vector[T], T, Op.type] =
    new BinaryUpdateRegistry[Vector[T], T, Op.type] {
      override def bindingMissing(a: Vector[T], b: T): Unit = {
        var i = 0
        while (i < a.length) {
          a(i) = op(a(i), b)
          i += 1
        }
      }
    }

  @expand
  implicit def v_s_UpdateOp[
      @expand.args(OpAdd, OpSub, OpMulScalar, OpMulMatrix, OpDiv, OpSet, OpMod, OpPow) Op <: OpType,
      T: Field: ClassTag](
      implicit @expand.sequence[Op]({ f.+(_, _) }, { f.-(_, _) }, { f.*(_, _) }, { f.*(_, _) }, { f./(_, _) }, {
        (_x, _y) =>
          _y
      }, { f.%(_, _) }, { f.pow(_, _) })
      op: Op.Impl2[T, T, T]): BinaryUpdateRegistry[Vector[T], T, Op.type] =
    new BinaryUpdateRegistry[Vector[T], T, Op.type] {
      val f = implicitly[Field[T]]
      override def bindingMissing(a: Vector[T], b: T): Unit = {
        var i = 0
        while (i < a.length) {
          a(i) = op(a(i), b)
          i += 1
        }
      }
    }
  @expand
  @expand.valify
  implicit def canDot_V_V[@expand.args(Int, Long, Float, Double) T](
      implicit @expand.sequence[T](0, 0L, 0.0f, 0.0) zero: T)
    : BinaryRegistry[Vector[T], Vector[T], breeze.linalg.operators.OpMulInner.type, T] = {
    new BinaryRegistry[Vector[T], Vector[T], breeze.linalg.operators.OpMulInner.type, T] {
      override def bindingMissing(a: Vector[T], b: Vector[T]): T = {
        require(b.length == a.length, "Vectors must be the same length!")
        if (a.activeSize > b.activeSize) {
          bindingMissing(b, a)
        } else {
          var result: T = zero
          for ((k, v) <- a.activeIterator) {
            result += v * b(k)
          }
          result
        }
      }
    }
  }

  implicit def canDot_V_V[T: ClassTag: Semiring]
    : BinaryRegistry[Vector[T], Vector[T], breeze.linalg.operators.OpMulInner.type, T] = {
    new BinaryRegistry[Vector[T], Vector[T], breeze.linalg.operators.OpMulInner.type, T] {
      val s = implicitly[Semiring[T]]
      override def bindingMissing(a: Vector[T], b: Vector[T]): T = {
        require(b.length == a.length, "Vectors must be the same length!")
        if (a.activeSize > b.activeSize) {
          bindingMissing(b, a)
        } else {
          var result: T = s.zero
          for ((k, v) <- a.activeIterator) {
            result = s.+(result, s.*(v, b(k)))
          }
          result
        }
      }
    }
  }
  @expand
  @expand.valify
  implicit def impl_scaleAdd_InPlace_V_T_V[@expand.args(Int, Double, Float, Long) T]
    : TernaryUpdateRegistry[Vector[T], T, Vector[T], scaleAdd.type] = {
    new TernaryUpdateRegistry[Vector[T], T, Vector[T], scaleAdd.type] {
      override def bindingMissing(a: Vector[T], s: T, b: Vector[T]): Unit = {
        require(b.length == a.length, "Vectors must be the same length!")
        if (s == 0) return

        var i = 0
        for ((k, v) <- b.activeIterator) {
          a(k) += s * v
          i += 1
        }
      }
    }
  }


  @expand
  @expand.valify
  implicit def zipValuesImpl_V_V[@expand.args(Int, Double, Float, Long) T]
    : BinaryRegistry[Vector[T], Vector[T], zipValues.type, ZippedValues[T, T]] = {
    new BinaryRegistry[Vector[T], Vector[T], zipValues.type, ZippedValues[T, T]] {
      protected override def bindingMissing(a: Vector[T], b: Vector[T]): ZippedValues[T, T] = {
        require(a.length == b.length, "vector dimension mismatch")
        ZippedVectorValues(a, b)
      }
    }
  }


}