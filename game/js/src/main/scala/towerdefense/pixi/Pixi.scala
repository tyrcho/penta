package towerdefense.pixi

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import org.scalajs.dom

// Minimal facade for the PixiJS v8 global (loaded from CDN in index.html),
// covering only what GameApp needs. See text-maps' LZString facade for the same pattern.

@js.native
@JSGlobal("PIXI.Application")
class Application extends js.Object:
  def init(options: js.Object): js.Promise[Unit] = js.native
  val canvas: dom.html.Canvas = js.native
  val stage: Container = js.native
  val ticker: Ticker = js.native
  val screen: Rectangle = js.native

@js.native
trait Rectangle extends js.Object:
  val width: Double = js.native
  val height: Double = js.native

@js.native
@JSGlobal("PIXI.Container")
class Container extends js.Object:
  def addChild(child: Container): Container = js.native
  def removeChild(child: Container): Container = js.native
  var x: Double = js.native
  var y: Double = js.native
  var rotation: Double = js.native
  var width: Double = js.native
  var height: Double = js.native
  var alpha: Double = js.native
  var scale: ScaleObj = js.native
  var eventMode: String = js.native
  def on(event: String, handler: js.Function1[FederatedPointerEvent, Unit]): Unit = js.native

@js.native
trait ScaleObj extends js.Object:
  def set(v: Double): Unit = js.native

@js.native
@JSGlobal("PIXI.Graphics")
class Graphics extends Container:
  def rect(x: Double, y: Double, w: Double, h: Double): Graphics = js.native
  def circle(x: Double, y: Double, radius: Double): Graphics = js.native
  def fill(color: Int): Graphics = js.native
  def stroke(options: js.Object): Graphics = js.native
  def clear(): Graphics = js.native

@js.native
trait Ticker extends js.Object:
  def add(handler: js.Function1[Ticker, Unit]): Unit = js.native
  val deltaMS: Double = js.native

@js.native
trait FederatedPointerEvent extends js.Object:
  val globalX: Double = js.native
  val globalY: Double = js.native

@js.native
trait Texture extends js.Object

@js.native
@JSGlobal("PIXI.Assets")
object Assets extends js.Object:
  def load(urls: js.Array[String]): js.Promise[js.Dictionary[Texture]] = js.native

@js.native
@JSGlobal("PIXI.Sprite")
class Sprite(initialTexture: Texture) extends Container:
  var anchor: AnchorObj = js.native
  var tint: Int = js.native
  var texture: Texture = js.native

@js.native
trait AnchorObj extends js.Object:
  def set(v: Double): Unit = js.native

@js.native
@JSGlobal("PIXI.AnimatedSprite")
class AnimatedSprite(frames: js.Array[Texture]) extends Container:
  var anchor: AnchorObj = js.native
  var loop: Boolean = js.native
  var animationSpeed: Double = js.native
  var onComplete: js.Function0[Unit] = js.native
  var textures: js.Array[Texture] = js.native
  def play(): Unit = js.native
