package com.detroitlabs.kyleofori.doomrainbow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.floor;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

public class RainbowView extends FrameLayout {

    public enum IndicatorType {
        CIRCLE, ARC, NONE
    }

    private static final Paint BASE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint DEFAULT_BACKGROUND_ARC_PAINT = new Paint(BASE_PAINT);
    private static final Paint DEFAULT_CURRENT_VALUE_LABEL_PAINT = new Paint(BASE_PAINT);
    private static final Paint DEFAULT_EXTREME_VALUE_LABEL_PAINT = new Paint(BASE_PAINT);
    private static final Paint DEFAULT_GOAL_INDICATOR_PAINT = new Paint(BASE_PAINT);
    private static final Paint DEFAULT_FOREGROUND_ARC_PAINT = new Paint(BASE_PAINT);
    private static final Paint.Cap DEFAULT_ARC_STROKE_CAP = Paint.Cap.ROUND;
    private static final float DEFAULT_BACKGROUND_START_ANGLE = -135;
    private static final float DEFAULT_BACKGROUND_END_ANGLE = 135;
    private static final float DEFAULT_BACKGROUND_EXTREME_LABEL_PADDING = 15;
    private static final float DEFAULT_RADIUS_COEFFICIENT = .85f;
    private static final float DEFAULT_GOAL_VALUE = 90;
    private static final float DEFAULT_GOAL_ARC_LENGTH = 4;
    private static final float DEFAULT_CHILD_VIEW_ASPECT_RATIO = 2f;
    private static final long DEFAULT_ANIMATION_DURATION_MS = 2000;
    private static final int DEFAULT_MIN_VALUE = 0;
    private static final int DEFAULT_MAX_VALUE = 100;
    private static final int DEFAULT_ARC_STROKE_WIDTH = 20;
    private static final float DEFAULT_CURRENT_VALUE_LABEL_TEXT_SIZE = 40;
    private static final float DEFAULT_EXTREME_VALUE_LABEL_TEXT_SIZE = 60;
    private static final float LEVEL_TEXT_RADIUS_SCALE_FACTOR = 1.10f;

    static {
        initDefaultBackgroundArcPaint();
        initDefaultCurrentValueLabelPaint();
        initDefaultExtremeValueLabelPaint();
        initDefaultGoalIndicatorPaint();
        initDefaultForegroundArcPaint();
    }

    private static void initDefaultBackgroundArcPaint() {
        DEFAULT_BACKGROUND_ARC_PAINT.setStyle(Paint.Style.STROKE);
        DEFAULT_BACKGROUND_ARC_PAINT.setStrokeCap(DEFAULT_ARC_STROKE_CAP);
        DEFAULT_BACKGROUND_ARC_PAINT.setStrokeWidth(DEFAULT_ARC_STROKE_WIDTH);
        DEFAULT_BACKGROUND_ARC_PAINT.setColor(Color.GRAY);
    }

    private static void initDefaultCurrentValueLabelPaint() {
        DEFAULT_CURRENT_VALUE_LABEL_PAINT.setColor(Color.BLACK);
        DEFAULT_CURRENT_VALUE_LABEL_PAINT.setFakeBoldText(false);
        DEFAULT_CURRENT_VALUE_LABEL_PAINT.setTextSize(DEFAULT_CURRENT_VALUE_LABEL_TEXT_SIZE);
    }

    private static void initDefaultExtremeValueLabelPaint() {
        DEFAULT_EXTREME_VALUE_LABEL_PAINT.setColor(Color.BLACK);
        DEFAULT_EXTREME_VALUE_LABEL_PAINT.setFakeBoldText(true);
        DEFAULT_EXTREME_VALUE_LABEL_PAINT.setTextSize(DEFAULT_EXTREME_VALUE_LABEL_TEXT_SIZE);
        DEFAULT_EXTREME_VALUE_LABEL_PAINT.setTextAlign(Paint.Align.CENTER);
    }

    private static void initDefaultGoalIndicatorPaint() {
        DEFAULT_GOAL_INDICATOR_PAINT.setStyle(Paint.Style.STROKE);
        DEFAULT_GOAL_INDICATOR_PAINT.setStrokeCap(DEFAULT_ARC_STROKE_CAP);
        DEFAULT_GOAL_INDICATOR_PAINT.setStrokeWidth(DEFAULT_ARC_STROKE_WIDTH);
        DEFAULT_GOAL_INDICATOR_PAINT.setColor(Color.GREEN);
    }

    private static void initDefaultForegroundArcPaint() {
        DEFAULT_FOREGROUND_ARC_PAINT.setStyle(Paint.Style.STROKE);
        DEFAULT_FOREGROUND_ARC_PAINT.setStrokeCap(DEFAULT_ARC_STROKE_CAP);
        DEFAULT_FOREGROUND_ARC_PAINT.setStrokeWidth(DEFAULT_ARC_STROKE_WIDTH);
        DEFAULT_FOREGROUND_ARC_PAINT.setColor(Color.BLUE);
    }

    @Nullable private Paint customBackgroundArcPaint;
    @Nullable private Paint customCurrentValueLabelPaint;
    @Nullable private Paint customExtremeValueLabelPaint;
    @Nullable private Paint customGoalIndicatorPaint;
    @Nullable private Paint customForegroundArcPaint;

    @NonNull private IndicatorType goalIndicatorType = IndicatorType.NONE;

    private String minimumValueLabel, maximumValueLabel;
    private RectF doomRainbowRectF;
    private Rect childViewRect;
    private ValueAnimator animation;

    private int minimumValue, maximumValue;
    private float currentValue, goalValue;
    private float minimumBackgroundArcAngle, maximumBackgroundArcAngle;
    private float radius;
    private float viewWidthHalf;
    private float viewHeightHalf;
    private float valueToDraw;
    private boolean animateChangesInCurrentLevel = true;
    private boolean currentLevelText;
    private long animationDuration = DEFAULT_ANIMATION_DURATION_MS;

    /**
     * Aspect ratio of child view, such that
     *
     *     w = LAMBDA h
     *
     * where w is the width of the child view, and h is the height of the child view.
     */
    private float lambda = DEFAULT_CHILD_VIEW_ASPECT_RATIO;

    public RainbowView(final Context context) {
        this(context, null);
    }

    public RainbowView(final Context context, @Nullable final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RainbowView(
            final Context context,
            @Nullable final AttributeSet attrs,
            final int defStyleAttr) {

        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setSaveEnabled(true);
        setWillNotDraw(false);
        doomRainbowRectF = new RectF();
        childViewRect = new Rect();
        minimumValue = DEFAULT_MIN_VALUE;
        maximumValue = DEFAULT_MAX_VALUE;
        setMinimumBackgroundArcAngle(DEFAULT_BACKGROUND_START_ANGLE);
        setMaximumBackgroundArcAngle(DEFAULT_BACKGROUND_END_ANGLE);
        setGoalValue(DEFAULT_GOAL_VALUE);
        currentValue = minimumValue;
        resetValueToDraw();
        reanimate();
    }

    // Overrides

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int measuredWidth = getMeasuredWidth();

        //noinspection SuspiciousNameCombination
        setMeasuredDimension(measuredWidth, measuredWidth);

        final double circleInternalRadius = radius - getBackgroundArcPaint().getStrokeWidth() / 2;
        final double childViewHeight = 2 * circleInternalRadius / sqrt(1 + pow(lambda, 2));
        final double childViewWidth = lambda * childViewHeight;

        childViewRect.set(
                (int) ceil((getMeasuredWidth() - childViewWidth) / 2),
                (int) floor((getMeasuredHeight() - childViewHeight) / 2),
                (int) ceil((getMeasuredWidth() + childViewWidth) / 2),
                (int) floor((getMeasuredHeight() + childViewHeight) / 2)
        );

        final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                childViewRect.height(),
                MeasureSpec.EXACTLY
        );

        final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                childViewRect.width(),
                MeasureSpec.EXACTLY
        );

        if(getChildCount() != 1) {
            throw new IllegalStateException("This view must have exactly one child.");
        } else {
            getChildAt(0).measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(
            final boolean changed,
            final int left,
            final int top,
            final int right,
            final int bottom) {

        super.onLayout(changed, left, top, right, bottom);
        viewWidthHalf = getMeasuredWidth() / 2;
        viewHeightHalf = getMeasuredHeight() / 2;

        if(viewHeightHalf > viewWidthHalf) {
            radius = viewWidthHalf * DEFAULT_RADIUS_COEFFICIENT;
        } else {
            radius = viewHeightHalf * DEFAULT_RADIUS_COEFFICIENT;
        }

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            child.layout(
                    childViewRect.left,
                    childViewRect.top,
                    childViewRect.right,
                    childViewRect.bottom
            );
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        doomRainbowRectF.set(
                viewWidthHalf - radius,
                viewHeightHalf - radius,
                viewWidthHalf + radius,
                viewHeightHalf + radius);

        drawShiftedArc(canvas, doomRainbowRectF, minimumValue, maximumValue, getBackgroundArcPaint());

        drawShiftedArc(canvas, doomRainbowRectF, minimumValue, valueToDraw, getCurrentLevelArcPaint());

        drawCurrentLevelTextIfPresent(canvas);

        drawExtremeLabelsIfPresent(canvas);

        if (goalIndicatorType != IndicatorType.NONE) {
            drawIndicator(canvas);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState ss = new SavedState(superState);
        ss.currentLevelValue = currentValue;
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(final Parcelable state) {
        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        currentValue = ss.currentLevelValue;
        resetValueToDraw();
    }

    // Public API

    public void setPaintStrokeCap(final Paint.Cap strokeCap) {
        final Paint newBackgroundPaint = new Paint(getBackgroundArcPaint());
        newBackgroundPaint.setStrokeCap(strokeCap);
        customBackgroundArcPaint = newBackgroundPaint;
        final Paint newCurrentLevelPaint = new Paint(getCurrentLevelArcPaint());
        newCurrentLevelPaint.setStrokeCap(strokeCap);
        customForegroundArcPaint = newCurrentLevelPaint;
        final Paint newGoalPaint = new Paint(getGoalPaint());
        newGoalPaint.setStrokeCap(strokeCap);
        customGoalIndicatorPaint = newGoalPaint;
        invalidate();
    }

    public void setArcWidth(final float strokeWidth) {
        final Paint newBackgroundPaint = new Paint(getBackgroundArcPaint());
        newBackgroundPaint.setStrokeWidth(strokeWidth);
        customBackgroundArcPaint = newBackgroundPaint;
        final Paint newCurrentLevelPaint = new Paint(getCurrentLevelArcPaint());
        newCurrentLevelPaint.setStrokeWidth(strokeWidth);
        customForegroundArcPaint = newCurrentLevelPaint;
        final Paint newGoalPaint = new Paint(getGoalPaint());
        newGoalPaint.setStrokeWidth(strokeWidth);
        customGoalIndicatorPaint = newGoalPaint;
        invalidate();
    }

    public void setBackgroundArcColor(@ColorInt final int color) {
        final Paint newPaint = new Paint(getBackgroundArcPaint());
        newPaint.setColor(color);
        customBackgroundArcPaint = newPaint;
        invalidate();
    }

    public void setForegroundArcColor(@ColorInt final int color) {
        final Paint newPaint = new Paint(getCurrentLevelArcPaint());
        newPaint.setColor(color);
        customForegroundArcPaint = newPaint;
        invalidate();
    }

    /**
     * User should pass in a function that maps from the minimum and maximum value of the rainbowView
     * to the color code.
     */
    public void setCurrentLevelArcPaintColorFunction(
            final Float value,
            final Function<Integer, Integer> function) {

        final Paint newPaint = new Paint(getCurrentLevelArcPaint());
        newPaint.setColor(function.apply(Math.round(value)));
        customForegroundArcPaint = newPaint;
        invalidate();
    }

    public void setCurrentValueLabelTextColor(@ColorInt final int textColor) {
        final Paint newPaint = new Paint(getCurrentLevelTextPaint());
        newPaint.setColor(textColor);
        customCurrentValueLabelPaint = newPaint;
        invalidate();
    }

    public void setCurrentValueLabelTextSize(final float textSize) {
        final Paint newPaint = new Paint(getCurrentLevelTextPaint());
        newPaint.setTextSize(textSize);
        customCurrentValueLabelPaint = newPaint;
        invalidate();
    }

    public void setExtremeValueLabelTextColor(@ColorInt final int color) {
        final Paint newPaint = new Paint(getExtremeLabelTextPaint());
        newPaint.setColor(color);
        customExtremeValueLabelPaint = newPaint;
        invalidate();
    }

    public void setExtremeValueLabelTextSize(final float textSize) {
        final Paint newPaint = new Paint(getExtremeLabelTextPaint());
        newPaint.setTextSize(textSize);
        customExtremeValueLabelPaint = newPaint;
        invalidate();
    }

    public void setGoalIndicatorColor(@ColorInt final int color) {
        final Paint newPaint = new Paint(getGoalPaint());
        newPaint.setColor(color);
        customGoalIndicatorPaint = newPaint;
        invalidate();
    }

    public void setGoalIndicatorType(@NonNull final IndicatorType indicatorType) {
        this.goalIndicatorType = indicatorType;
        invalidate();
    }

    public void changeCurrentValueBy(final float difference) {
        setCurrentValue(currentValue + difference);
    }

    /**
     * Note: if you intend to use numbers for minimumValueLabel and maximumValueLabel's values,
     * change those to reflect the range when you set range.
     */
    public void setRepresentedRange(final int minimumValue, final int maximumValue) {
        this.minimumValue = minimumValue;
        this.maximumValue = maximumValue;
    }

    public void setCurrentValue(final float currentValue) {
        final float previousValue = this.currentValue;

        this.currentValue = Math.min(
                Math.max(minimumValue, currentValue), maximumValue);

        if(animation != null) {
            animation.cancel();
        }

        if(animateChangesInCurrentLevel) {
            animateBetweenValues(previousValue, currentValue);
        } else {
            valueToDraw = this.currentValue;
        }

        invalidate();
    }

    public void setGoalValue(final float goalValue) {
        this.goalValue = goalValue;
        invalidate();
    }

    public void setMaximumValueLabel(final String maximumValueLabel) {
        this.maximumValueLabel = maximumValueLabel;
        invalidate();
    }

    public void setMinimumValueLabel(final String minimumValueLabel) {
        this.minimumValueLabel = minimumValueLabel;
        invalidate();
    }

    public void setAnimationDuration(final long animationDuration) {
        this.animationDuration = animationDuration;
    }

    public void setShouldAnimateChangesInCurrentLevel(final boolean animateChangesInCurrentLevel) {
        this.animateChangesInCurrentLevel = animateChangesInCurrentLevel;
    }

    public void setChildViewAspectRatio(final float lambda) {
        this.lambda = lambda;
        requestLayout();
    }

    public void setMinimumBackgroundArcAngle(final float minimumBackgroundArcAngle) {
        this.minimumBackgroundArcAngle = minimumBackgroundArcAngle;
        invalidate();
    }

    public void setMaximumBackgroundArcAngle(final float backgroundEndAngle) {
        this.maximumBackgroundArcAngle = backgroundEndAngle;
        invalidate();
    }

    // Private implementation

    @NonNull
    private Paint getBackgroundArcPaint() {
        return getPaint(customBackgroundArcPaint, DEFAULT_BACKGROUND_ARC_PAINT);
    }

    @NonNull
    private Paint getCurrentLevelTextPaint() {
        return getPaint(customCurrentValueLabelPaint, DEFAULT_CURRENT_VALUE_LABEL_PAINT);
    }

    @NonNull
    private Paint getExtremeLabelTextPaint() {
        return getPaint(customExtremeValueLabelPaint, DEFAULT_EXTREME_VALUE_LABEL_PAINT);
    }

    @NonNull
    private Paint getGoalPaint() {
        return getPaint(customGoalIndicatorPaint, DEFAULT_GOAL_INDICATOR_PAINT);
    }

    @NonNull
    private Paint getCurrentLevelArcPaint() {
        return getPaint(customForegroundArcPaint, DEFAULT_FOREGROUND_ARC_PAINT);
    }

    @NonNull
    private Paint getPaint(@Nullable final Paint customPaint, @NonNull final Paint defaultPaint) {
        return customPaint != null ? customPaint : defaultPaint;
    }

    private boolean hasCurrentLevelText() {
        return currentLevelText;
    }

    private void drawCurrentLevelTextIfPresent(final Canvas canvas) {
        if(hasCurrentLevelText()) {
            final double currentLevelAngle = AngleUtils.convertFromValueToAngle(currentValue,
                    getAnglesRange(),
                    getValuesRange()
            );

            final double angleInRadians = Math.toRadians(currentLevelAngle - 90);

            canvas.drawText(
                    String.valueOf(Math.round(currentValue)),
                    viewWidthHalf + (float) cos(angleInRadians) * radius * LEVEL_TEXT_RADIUS_SCALE_FACTOR,
                    viewHeightHalf + (float) sin(angleInRadians) * radius * LEVEL_TEXT_RADIUS_SCALE_FACTOR,
                    getCurrentLevelTextPaint()
            );
        }
    }

    private void drawShiftedArc(
            final Canvas canvas,
            final RectF rectF,
            final float startValue,
            final float endValue,
            final Paint paint) {

        final float startAngle = AngleUtils.convertFromValueToAngle(
                startValue,
                getAnglesRange(),
                getValuesRange());

        final float endAngle = AngleUtils.convertFromValueToAngle(
                endValue,
                getAnglesRange(),
                getValuesRange());

        canvas.drawArc(rectF, startAngle - 90, (endAngle - startAngle), false, paint);
    }


    private void drawExtremeLabelsIfPresent(final Canvas canvas) {
        final float yCoord = viewHeightHalf + radius;
        final float floatViewWidthHalf = (float) this.getMeasuredWidth()/2;

        if(minimumValueLabel != null) {
            final float minValRadiusCosCoefficient
                    = AngleUtils.getRadiusCosineCoefficient(
                            minimumBackgroundArcAngle - DEFAULT_BACKGROUND_EXTREME_LABEL_PADDING);

            final float xCoord = floatViewWidthHalf + minValRadiusCosCoefficient * radius;
            drawValue(canvas, minimumValueLabel, xCoord, yCoord);
        }

        if(maximumValueLabel != null) {
            final float maxValRadiusCosCoefficient
                    = AngleUtils.getRadiusCosineCoefficient(
                            maximumBackgroundArcAngle + DEFAULT_BACKGROUND_EXTREME_LABEL_PADDING);

            final float xCoord = floatViewWidthHalf - maxValRadiusCosCoefficient * radius;
            drawValue(canvas, maximumValueLabel, xCoord, yCoord);
        }
    }

    private void drawIndicator(final Canvas canvas) {
        switch(goalIndicatorType) {
            case CIRCLE:
                final float goalAngle = AngleUtils.convertFromValueToAngle(
                        goalValue,
                        getAnglesRange(),
                        getValuesRange());

                final double goalAngleRadians = Math.toRadians(goalAngle - 90);

                canvas.drawPoint(
                        viewWidthHalf + (float) cos(goalAngleRadians) * radius,
                        viewHeightHalf + (float) sin(goalAngleRadians) * radius,
                        getGoalPaint());

                break;
            case ARC:
                drawShiftedArc(
                        canvas,
                        doomRainbowRectF,
                        goalValue - DEFAULT_GOAL_ARC_LENGTH/2,
                        goalValue + DEFAULT_GOAL_ARC_LENGTH/2,
                        getGoalPaint());

                break;
            default:
                break;
        }
    }

    private void drawValue(
            final Canvas canvas,
            final String string,
            final float xCoord,
            final float yCoord) {

        canvas.drawText(string, xCoord, yCoord, getExtremeLabelTextPaint());
    }

    private void reanimate() {
        animateBetweenValues(minimumValue, currentValue);
    }

    private void resetValueToDraw() {
        valueToDraw = currentValue;
    }

    private void animateBetweenValues(final float startValue, final float stopValue) {
        animation = ValueAnimator.ofFloat(startValue, stopValue);

        animation.setDuration(animationDuration);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator valueAnimator) {
                valueToDraw = (float) valueAnimator.getAnimatedValue();
                RainbowView.this.invalidate();
            }
        });

        animation.start();
    }

    private int getValuesRange() {
        return maximumValue - minimumValue;
    }

    private float getAnglesRange() {
        return maximumBackgroundArcAngle - minimumBackgroundArcAngle;
    }

    private static class SavedState extends BaseSavedState {

        private float currentLevelValue;

        SavedState(final Parcelable superState) {
            super(superState);
        }

        private SavedState(final Parcel in) {
            super(in);
            currentLevelValue = in.readFloat();
        }

        @Override
        public void writeToParcel(final Parcel out, final int flags) {
            super.writeToParcel(out, flags);
            out.writeFloat(currentLevelValue);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {

            @Override
            public SavedState createFromParcel(final Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(final int size) {
                return new SavedState[size];
            }
        };
    }

}
