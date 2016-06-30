package org.nspl

import data._

case class DataElem(
    data: DataSource,
    xAxis: Axis,
    yAxis: Axis,
    renderers: Seq[DataRenderer],
    bounds: Bounds,
    tx: AffineTransform = AffineTransform.identity
) extends Renderable[DataElem] {
  def transform(tx: Bounds => AffineTransform) = this.copy(tx = tx(bounds).concat(this.tx))
}

trait Plots {

  type XYPlotArea = org.nspl.Elems5[org.nspl.ElemList[org.nspl.ShapeElem], org.nspl.ElemList[org.nspl.ShapeElem], org.nspl.ElemList[org.nspl.DataElem], org.nspl.Elems2[org.nspl.AxisElem, org.nspl.AxisElem], org.nspl.ElemList[org.nspl.ShapeElem]]

  def xyplotarea(
    data: Seq[(DataSource, List[DataRenderer])],
    xAxisSetting: AxisSettings = AxisSettings(LinearAxisFactory),
    yAxisSetting: AxisSettings = AxisSettings(LinearAxisFactory),
    origin: Option[Point] = None,
    xlim: Option[(Double, Double)] = None,
    ylim: Option[(Double, Double)] = None,
    axisMargin: Double = 0.05,
    xCol: Int = 0,
    yCol: Int = 1,
    xgrid: Boolean = true,
    ygrid: Boolean = true,
    frame: Boolean = true
  ): XYPlotArea = {

    val xMinMax =
      data.map(_._1.columnMinMax(xCol))

    val yMinMax =
      data.map(_._1.columnMinMax(yCol))

    val dataXMin = if (xlim.isDefined) 0.0 else xMinMax.map(_.min).min
    val dataXMax = if (xlim.isDefined) 0.0 else xMinMax.map(_.max).max
    val dataYMin = if (ylim.isDefined) 0.0 else yMinMax.map(_.min).min
    val dataYMax = if (ylim.isDefined) 0.0 else yMinMax.map(_.max).max

    val xMin = math.min(xlim.map(_._1).getOrElse {
      dataXMin - axisMargin * (dataXMax - dataXMin)
    }, origin.map(_.x).getOrElse(Double.MaxValue))

    val xMax = xlim.map(_._2).getOrElse {
      dataXMax + axisMargin * (dataXMax - dataXMin)
    }

    val yMin = math.min(ylim.map(_._1).getOrElse {
      dataYMin - axisMargin * (dataYMax - dataYMin)
    }, origin.map(_.y).getOrElse(Double.MaxValue))

    val yMax = ylim.map(_._2).getOrElse {
      dataYMax + axisMargin * (dataYMax - dataYMin)
    }

    val xAxis = xAxisSetting.axisFactory.make(xMin, xMax, xAxisSetting.width)
    val yAxis = yAxisSetting.axisFactory.make(yMin, yMax, yAxisSetting.width)

    val xMinV = xAxis.worldToView(xMin)
    val xMaxV = xAxis.worldToView(xMax)
    val yMinV = yAxis.worldToView(yMin)
    val yMaxV = yAxis.worldToView(yMax)

    val originWX1 = origin.map { origin =>
      xlim.map {
        case (a, b) =>
          if (origin.x < a) a
          else if (origin.x > b) b
          else origin.x
      } getOrElse origin.x
    } getOrElse xMin

    val originWY1 = origin.map { origin =>
      ylim.map {
        case (a, b) =>
          if (origin.y < a) a
          else if (origin.y > b) b
          else origin.y
      } getOrElse origin.y
    } getOrElse yMin

    val noXTick = if (origin.isEmpty) Nil else List(originWX1)
    val noYTick = if (origin.isEmpty) Nil else List(originWY1)

    val (xMajorTicks, xAxisElem) = xAxisSetting.renderable(xAxis, noXTick)
    val (yMajorTicks, yAxisElem) = yAxisSetting.renderable(
      yAxis,
      noYTick,
      horizontal = false
    )

    val originX = xAxis.worldToView(originWX1)
    val originY = yAxis.worldToView(originWY1)

    val axes = group(
      translate(xAxisElem, 0, -1 * originY),
      translate(
        yAxisElem, originX, -1 * yMaxV
      ),
      FreeLayout
    )

    val dataelem = sequence(data.toList.map {
      case (ds, drs) =>
        DataElem(ds, xAxis, yAxis, drs, axes.bounds, AffineTransform.reflectX)
    })

    val xgridElem = sequence(xMajorTicks map { w =>
      val v = xAxis.worldToView(w)
      ShapeElem(
        Shape.line(Point(v, yMinV), Point(v, -1 * yMaxV)),
        stroke = if (xgrid) Some(Stroke(1d)) else None,
        strokeColor = Color.gray5
      )
    })

    val frameStroke = if (frame) Some(Stroke(2d)) else None
    val frameElem = sequence(
      List(
        ShapeElem(
          Shape.line(Point(xMinV, -1 * yMaxV), Point(xMaxV, -1 * yMaxV)), stroke = frameStroke
        ),
        ShapeElem(
          Shape.line(Point(xMaxV, -1 * yMaxV), Point(xMaxV, yMinV)), stroke = frameStroke
        ),
        ShapeElem(
          Shape.line(Point(xMinV, yMinV), Point(xMaxV, yMinV)), stroke = frameStroke
        ),
        ShapeElem(
          Shape.line(Point(xMinV, -1 * yMaxV), Point(xMinV, yMinV)), stroke = frameStroke
        )
      )
    )

    val ygridElem = sequence(yMajorTicks map { w =>
      val v = yAxis.worldToView(w)
      ShapeElem(
        Shape.line(
          Point(xMinV, -1 * v + xMinV),
          Point(xMaxV, -1 * v + xMinV)
        ),
        stroke = if (ygrid) Some(Stroke(1d)) else None,
        strokeColor = Color.gray5
      )
    })

    group(xgridElem, ygridElem, dataelem, axes, frameElem, FreeLayout)

  }

  type Figure[T <: Renderable[T]] = Elems2[TextBox, Elems2[Elems2[TextBox, T], TextBox]]

  /* Decorates with main, xlab and ylab labels. */
  def figure[T <: Renderable[T]](
    plot: T,
    main: String = "",
    mainFontSize: RelFontSize = 1.2 fts,
    mainDistance: RelFontSize = 1.2 fts,
    xlab: String = "",
    xlabFontSize: RelFontSize = 1.0 fts,
    xlabDistance: RelFontSize = 1.0 fts,
    xlabAlignment: Alignment = Center,
    ylab: String = "",
    ylabFontSize: RelFontSize = 1.0 fts,
    ylabDistance: RelFontSize = 1.0 fts,
    ylabAlignment: Alignment = Center
  ): Figure[T] = {
    val renderedPlot = plot
    val mainBox = TextBox(main, fontSize = mainFontSize, width = Some(renderedPlot.bounds.w))
    val xlabBox = TextBox(xlab, fontSize = xlabFontSize, width = Some(renderedPlot.bounds.w))
    val ylabBox = TextBox(ylab, fontSize = ylabFontSize, width = Some(renderedPlot.bounds.h))

    group(
      rotate(ylabBox, 0.5 * math.Pi),
      group(
        group(mainBox, renderedPlot, VerticalStack(Center, mainDistance)),
        xlabBox,
        VerticalStack(Center, xlabDistance)
      ),
      HorizontalStack(Center, ylabDistance)
    )

  }

  sealed trait LegendElem
  case class PointLegend(shape: Shape, color: Color) extends LegendElem
  case class LineLegend(stroke: Stroke, color: Color) extends LegendElem

  type Legend = ElemList[Elems2[ShapeElem, TextBox]]

  def legend(
    entries: List[(String, LegendElem)],
    fontSize: RelFontSize = 1.0 fts,
    width: RelFontSize = 30 fts
  ): Legend = {
    sequence(entries.map {
      case (text, elem) =>
        val elem1 = elem match {
          case PointLegend(s, c) => fitToBounds(ShapeElem(s, fill = c), Bounds(0, 0, 1 fts, 1 fts))
          case LineLegend(s, c) =>
            fitToBounds(
              ShapeElem(
                Shape.line(Point(0, 0), Point(1 fts, 0)),
                strokeColor = c,
                stroke = Some(s)
              ),
              Bounds(0, 0, 1 fts, 1 fts)
            )
        }
        group(
          elem1,
          TextBox(text, fontSize = fontSize, width = Some(width)),
          HorizontalStack(Center, 1 fts)
        )
    }, VerticalStack(Left))
  }

  type HeatmapLegend = Elems2[ElemList[ShapeElem], Elems3[ShapeElem, ElemList[Elems2[ShapeElem, TextBox]], ElemList[ShapeElem]]]

  def heatmapLegend(
    min: Double,
    max: Double,
    color: Colormap = HeatMapColors(0d, 1d),
    fontSize: RelFontSize = 1.0 fts,
    width: RelFontSize = 10 fts,
    height: RelFontSize = 1 fts
  ): HeatmapLegend = {

    val color1 = color.withRange(min, max)

    val axisSettings = AxisSettings(
      LinearAxisFactory,
      fontSize = fontSize,
      width = width,
      numTicks = 2,
      tickAlignment = 1
    )
    val axis = axisSettings.axisFactory.make(min, max, width)

    val (majorTicks, axisElem) =
      axisSettings.renderable(
        axis,
        horizontal = false
      )

    val n = 500
    val space = (max - min) / n.toDouble
    val ticks = sequence(
      ((0 until n toList) map { i =>
        val world = axis.min + i * space
        val view = axis.worldToView(min) - axis.worldToView(world)
        ShapeElem(
          Shape.line(Point(0d, view), Point(height, view)),
          stroke = Some(Stroke(2d)),
          strokeColor = color1(world)
        )
      })
    )

    group(ticks, translate(axisElem, 0, -1 * axis.worldToView(max)), FreeLayout)

  }

}
