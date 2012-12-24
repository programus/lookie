package org.programus.nxt.android.lookie_rc.widgets;

import org.programus.nxt.android.lookie_rc.R;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class SpeedBar extends View {
	
	private final static int INDICATOR_SIZE = 10;
	private final static int MIN_WIDTH = 10;
	private final static int MIN_HEIGHT = 10;
	
	public final static int MAX_ABS_VALUE = 1000;
	
	private Paint strokePaint;
	private Paint fillPaint;
	private Paint overPaint;
	private Paint indicatorPaint;
	private Paint indicatorStrokePaint;
	
	private float targetValue;
	private float actualValue;
	private boolean overflow;
	
	final private RectF lIndicator = new RectF();
	final private RectF rIndicator = new RectF();
	
	private float oh;
	private float max;
	private float r;
	final private Point area = new Point();
	final private PointF margin = new PointF();
	final private RectF border = new RectF();
	
	private OnValueChangedListener onValueChangedListener;
	
	public static interface OnValueChangedListener {
		void onTargetValueChanged(SpeedBar sb, float oldValue, float newValue, float rawValue);
		void onActualValueChanged(SpeedBar sb, float oldValue, float newValue, float rawValue);
	}

	public SpeedBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.init(context, attrs);
	}

	public SpeedBar(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public SpeedBar(Context context) {
		this(context, null);
	}
	
	private void init(Context context, AttributeSet attrs) {
		this.strokePaint = new Paint();
		this.strokePaint.setStyle(Paint.Style.STROKE);
		this.strokePaint.setStrokeWidth(0);
		this.setupPaint(this.strokePaint);
		this.fillPaint = new Paint();
		this.fillPaint.setStyle(Paint.Style.FILL);
		this.setupPaint(this.fillPaint);
		this.overPaint = new Paint();
		this.overPaint.setStyle(Paint.Style.FILL);
		this.setupPaint(this.overPaint);
		this.indicatorPaint = new Paint();
		this.indicatorPaint.setStyle(Paint.Style.FILL);
		this.setupPaint(this.indicatorPaint);
		this.indicatorStrokePaint = new Paint();
		this.indicatorStrokePaint.setStyle(Paint.Style.STROKE);
		this.indicatorStrokePaint.setStrokeWidth(0);
		this.setupPaint(this.indicatorStrokePaint);
		
		Resources res = this.getResources();
		final int defaultFillColor = res.getColor(R.color.speedbar_default_color);
		final int defaultOverflowColor = res.getColor(R.color.speedbar_default_overflow_color);
		final int defaultStrokeColor = res.getColor(R.color.speedbar_default_stroke_color);
		final int defaultIndicatorColor = res.getColor(R.color.speedbar_default_indicator_color);
		
		if (attrs != null) {
			TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SpeedBar);
			this.strokePaint.setColor(a.getColor(R.styleable.SpeedBar_strokeColor, defaultStrokeColor));
			this.fillPaint.setColor(a.getColor(R.styleable.SpeedBar_color, defaultFillColor));
			this.overPaint.setColor(a.getColor(R.styleable.SpeedBar_overflowColor, defaultOverflowColor));
			this.indicatorPaint.setColor(a.getColor(R.styleable.SpeedBar_indicatorColor, defaultIndicatorColor));
			this.indicatorStrokePaint.setColor(a.getColor(R.styleable.SpeedBar_indicatorColor, defaultIndicatorColor));
		} else {
			this.strokePaint.setColor(defaultStrokeColor);
			this.fillPaint.setColor(defaultFillColor);
			this.overPaint.setColor(defaultOverflowColor);
			this.indicatorPaint.setColor(defaultIndicatorColor);
			this.indicatorStrokePaint.setColor(defaultIndicatorColor);
		}
	}
	
	private void setupPaint(final Paint p) {
		p.setAntiAlias(true);
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		// calculate data
		final float ah = oh - this.getActualPxValue();
		final float th = oh - this.getTargetPxValue();
		lIndicator.set(border.left - INDICATOR_SIZE, th - r, border.left, th + r);
		rIndicator.set(border.right, lIndicator.top, border.right + INDICATOR_SIZE, lIndicator.bottom);
		
		// draw data
		canvas.save();
		// actual value gauge
		canvas.drawRect(border.left, oh, border.right, ah, this.overflow ? this.overPaint : this.fillPaint);
		// zero line
		canvas.drawLine(border.left, oh, border.right, oh, this.strokePaint);
		// target value line
		canvas.drawLine(border.left, th, border.right, th, this.indicatorStrokePaint);
		// bar border
		canvas.drawRect(border, this.strokePaint);
		// fill indicator
		canvas.drawOval(lIndicator, this.indicatorPaint);
		canvas.drawOval(rIndicator, this.indicatorPaint);
		// stroke indicator
		canvas.drawOval(lIndicator, this.strokePaint);
		canvas.drawOval(rIndicator, this.strokePaint);
		
		canvas.restore();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		this.setMeasuredDimension(this.measureSize(widthMeasureSpec, true), this.measureSize(heightMeasureSpec, false));
	}
	
	private int measureSize(int spec, boolean isWidth) {
		int result = 0;
		final int specMode = MeasureSpec.getMode(spec);
		final int specSize = MeasureSpec.getSize(spec);
		if (specMode == MeasureSpec.EXACTLY) {
			result = specSize;
		} else {
			if (isWidth) {
				result = Math.min(specSize, MIN_WIDTH + (INDICATOR_SIZE << 1));
			} else {
				result = Math.min(specSize, (MIN_HEIGHT << 1) + 1);
			}
		}
		return result;
	}

	public float getTargetValue() {
		return targetValue;
	}

	public void setTargetValue(float targetValue) {
		float rawValue = targetValue;
		if (targetValue > MAX_ABS_VALUE) {
			targetValue = MAX_ABS_VALUE;
		} else if (targetValue < -MAX_ABS_VALUE) {
			targetValue = -MAX_ABS_VALUE;
		}
		if (this.onValueChangedListener != null && this.targetValue != rawValue) {
			this.onValueChangedListener.onTargetValueChanged(this, this.targetValue, targetValue, rawValue);
		}
		this.targetValue = targetValue;
	}
	
	public void setTargetPxValue(float pxValue) {
		float value = pxValue * MAX_ABS_VALUE / this.max;
		this.setTargetValue(value);
	}
	
	public float getTargetPxValue() {
		return this.targetValue * this.max / MAX_ABS_VALUE;
	}

	public float getActualValue() {
		return actualValue;
	}

	public void setActualValue(float actualValue) {
		float rawValue = actualValue;
		if (actualValue > MAX_ABS_VALUE) {
			this.overflow = true;
			actualValue = MAX_ABS_VALUE;
		} else if (actualValue < -MAX_ABS_VALUE) {
			this.overflow = true;
			actualValue = -MAX_ABS_VALUE;
		} else {
			this.overflow = false;
		}
		if (this.onValueChangedListener != null && this.actualValue != rawValue) {
			this.onValueChangedListener.onActualValueChanged(this, this.actualValue, actualValue, rawValue);
		}
		this.actualValue = actualValue;
	}
	
	public void setActualPxValue(float pxValue) {
		float value = pxValue * MAX_ABS_VALUE / this.max;
		this.setActualValue(value);
	}
	
	public float getActualPxValue() {
		return this.actualValue * this.max / MAX_ABS_VALUE;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		this.area.x = w - 1;
		this.area.y = h - 1;
		this.oh = (float) h / 2;
		this.margin.y = (float) INDICATOR_SIZE / 2;
		this.margin.x = INDICATOR_SIZE;
		this.max = this.oh - this.margin.y;
		if (this.max < 0) {
			this.max = 0;
		}
		
		this.r = (float) INDICATOR_SIZE / 2;
		
		this.border.left = this.margin.x;
		this.border.right = this.area.x - this.margin.x;
		this.border.top = this.margin.y;
		this.border.bottom = this.area.y - this.margin.y;
	}

	public OnValueChangedListener getOnValueChangedListener() {
		return onValueChangedListener;
	}

	public void setOnValueChangedListener(OnValueChangedListener listener) {
		this.onValueChangedListener = listener;
	}

}
