/*
 * {{{ header & license
 * Copyright (c) 2007 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package org.xhtmlrenderer.newtable;

import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

import org.xhtmlrenderer.css.constants.CSSName;
import org.xhtmlrenderer.css.constants.IdentValue;
import org.xhtmlrenderer.css.style.CalculatedStyle;
import org.xhtmlrenderer.css.style.CssContext;
import org.xhtmlrenderer.css.style.Length;
import org.xhtmlrenderer.css.style.derived.BorderPropertySet;
import org.xhtmlrenderer.css.style.derived.RectPropertySet;
import org.xhtmlrenderer.layout.CollapsedBorderSide;
import org.xhtmlrenderer.layout.FloatManager;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.render.BorderPainter;
import org.xhtmlrenderer.render.Box;
import org.xhtmlrenderer.render.RenderingContext;

public class TableCellBox extends BlockBox {
    public static final TableCellBox SPANNING_CELL = new TableCellBox();
    
    private int _row;
    private int _col;
    
    private TableBox _table;
    private TableSectionBox _section;
    
    private BorderPropertySet _collapsedLayoutBorder;
    private BorderPropertySet _collapsedPaintingBorder;
    
    private CollapsedBorderValue _collapsedBorderTop;
    private CollapsedBorderValue _collapsedBorderRight;
    private CollapsedBorderValue _collapsedBorderBottom;
    private CollapsedBorderValue _collapsedBorderLeft;
    
    // 'double', 'solid', 'dashed', 'dotted', 'ridge', 'outset', 'groove', and the lowest: 'inset'. 
    private static final int[] BORDER_PRIORITIES = new int[IdentValue.getIdentCount()];
    
    static {
        BORDER_PRIORITIES[IdentValue.DOUBLE.FS_ID] = 1;
        BORDER_PRIORITIES[IdentValue.SOLID.FS_ID] = 2;
        BORDER_PRIORITIES[IdentValue.DASHED.FS_ID] = 3;
        BORDER_PRIORITIES[IdentValue.DOTTED.FS_ID] = 4;
        BORDER_PRIORITIES[IdentValue.RIDGE.FS_ID] = 5;
        BORDER_PRIORITIES[IdentValue.OUTSET.FS_ID] = 6;
        BORDER_PRIORITIES[IdentValue.GROOVE.FS_ID] = 7;
        BORDER_PRIORITIES[IdentValue.INSET.FS_ID] = 8;
    }
    
    private static final int BCELL = 10;
    private static final int BROW = 9;
    private static final int BROWGROUP = 8;
    private static final int BCOL = 7;
    private static final int BTABLE = 6;
    
    public TableCellBox() {
    }
    
    public BorderPropertySet getBorder(CssContext cssCtx) {
        if (getTable().getStyle().isCollapseBorders()) {
            // Should always be non-null, but might not be if layout code crashed
            return _collapsedLayoutBorder == null ? 
                    BorderPropertySet.ALL_ZEROS : _collapsedLayoutBorder;
        } else {
            return super.getBorder(cssCtx);
        }
    }
    
    public void calcCollapsedBorder(CssContext c) {
        CollapsedBorderValue top = collapsedTopBorder(c);
        CollapsedBorderValue right = collapsedRightBorder(c);
        CollapsedBorderValue bottom = collapsedBottomBorder(c);
        CollapsedBorderValue left = collapsedLeftBorder(c);
        
        _collapsedPaintingBorder = new BorderPropertySet(top, right, bottom, left);
        
        // Give the extra pixel to top and left.
        top.setWidth((top.width()+1)/2);
        right.setWidth(right.width()/2);
        bottom.setWidth(bottom.width()/2);
        left.setWidth((left.width()+1)/2);
        
        _collapsedLayoutBorder = new BorderPropertySet(top, right, bottom, left);
        
        _collapsedBorderTop = top;
        _collapsedBorderRight = right;
        _collapsedBorderBottom = bottom;
        _collapsedBorderLeft = left;
    }

    public int getCol() {
        return _col;
    }

    public void setCol(int col) {
        _col = col;
    }

    public int getRow() {
        return _row;
    }

    public void setRow(int row) {
        _row = row;
    }
    
    public void layout(LayoutContext c) {
        super.layout(c);
    }
    
    public TableBox getTable() {
        // cell -> row -> section -> table
        if (_table == null) {
            _table = (TableBox)getParent().getParent().getParent();
        }
        return _table;
    }
    
    protected TableSectionBox getSection() {
        if (_section == null) {
            _section = (TableSectionBox)getParent().getParent();
        }
        return _section;
    }
    
    public Length getOuterStyleWidth(CssContext c) {
        Length result = getStyle().asLength(c, CSSName.WIDTH);
        if (result.isVariable() || result.isPercent()) {
            return result;
        }
        
        int bordersAndPadding = 0;
        BorderPropertySet border = getBorder(c);
        bordersAndPadding += (int)border.left() + (int)border.right();
        
        RectPropertySet padding = getPadding(c);
        bordersAndPadding += (int)padding.left() + (int)padding.right();
        
        result.setValue(result.value() + bordersAndPadding);
        
        return result;
    }
    
    public Length getOuterStyleOrColWidth(CssContext c) {
        Length result = getOuterStyleWidth(c);
        if (getStyle().getColSpan() > 1 || ! result.isVariable()) {
            return result;
        }
        TableColumn col = getTable().colElement(getCol());
        if (col != null) {
            // XXX Need to add in collapsed borders from cell (if collapsing borders)
            result = col.getStyle().asLength(c, CSSName.WIDTH);
        }
        return result;
    }
    
    public void setLayoutWidth(LayoutContext c, int width) {
        calcDimensions(c);
        
        setContentWidth(width - getLeftMBP() - getRightMBP());
    }
    
    public boolean isAutoHeight() {
        return getStyle().isAutoHeight() || ! getStyle().hasAbsoluteUnit(CSSName.HEIGHT);
    }
    
    public int calcBaseline(LayoutContext c) {
        int result = super.calcBaseline(c);
        if (result != NO_BASELINE) {
            return result;
        } else {
            Rectangle contentArea = getContentAreaEdge(getAbsX(), getAbsY(), c);
            return (int)contentArea.getY();
        }
    }
    
    public void moveContent(LayoutContext c, final int deltaY) {
        for (int i = 0; i < getChildCount(); i++) {
            Box b = getChild(i);
            b.setY(b.getY() + deltaY);
        }
        
        getPersistentBFC().getFloatManager().performFloatOperation(
                new FloatManager.FloatOperation() {
                    public void operate(Box floater) {
                        floater.setY(floater.getY() + deltaY);
                    }
                });
        
        calcChildLocations();
    }
    
    public IdentValue getVerticalAlign() {
        IdentValue val = getStyle().getIdent(CSSName.VERTICAL_ALIGN);
        
        if (val == IdentValue.TOP || val == IdentValue.MIDDLE || val == IdentValue.BOTTOM) {
            return val;
        } else {
            return IdentValue.BASELINE;
        }
    }
    
    private boolean isPaintBackgroundsAndBorders() {
        boolean showEmpty = getStyle().isShowEmptyCells();
        // XXX Not quite right, but good enough for now 
        // (e.g. absolute boxes will be counted as content here when the spec 
        // says the cell should be treated as empty).  
        return showEmpty || getChildrenContentType() != BlockBox.CONTENT_EMPTY;
                    
    }
    
    public void paintBackground(RenderingContext c) {
        if (isPaintBackgroundsAndBorders() && getStyle().isVisible()) {
            Rectangle bounds = getPaintingBorderEdge(c);
            Rectangle imageContainer;
            
            TableColumn column = getTable().colElement(getCol());
            if (column != null) {
                c.getOutputDevice().paintBackground(
                        c, column.getStyle(), 
                        bounds, getTable().getColumnBounds(c, getCol()));
            }
            
            Box row = getParent();
            Box section = row.getParent();
            
            CalculatedStyle tableStyle = getTable().getStyle();
            
            CalculatedStyle sectionStyle = section.getStyle();
            
            imageContainer = section.getPaintingBorderEdge(c);
            imageContainer.y += tableStyle.getBorderVSpacing(c);
            imageContainer.height -= tableStyle.getBorderVSpacing(c);
            imageContainer.x += tableStyle.getBorderHSpacing(c);
            imageContainer.width -= 2*tableStyle.getBorderHSpacing(c);
            
            c.getOutputDevice().paintBackground(c, sectionStyle, bounds, imageContainer);
            
            CalculatedStyle rowStyle = row.getStyle();
            
            imageContainer = row.getPaintingBorderEdge(c);
            imageContainer.x += tableStyle.getBorderHSpacing(c);
            imageContainer.width -= 2*tableStyle.getBorderHSpacing(c);
            
            c.getOutputDevice().paintBackground(c, rowStyle, bounds, imageContainer);
            
            
            super.paintBackground(c);
        }
    }
    
    public void paintBorder(RenderingContext c) {
        if (isPaintBackgroundsAndBorders() && ! hasCollapsedPaintingBorder()) {
            // Collapsed table borders are painted separately
            super.paintBorder(c);
        }
    }
    
    public void paintCollapsedBorder(RenderingContext c, int side) {
        c.getOutputDevice().paintCollapsedBorder(
                c, getCollapsedPaintingBorder(), getCollapsedBorderBounds(c), side);
    }
    
    protected boolean isFixedWidthAdvisoryOnly() {
        return getTable().getStyle().isIdent(CSSName.TABLE_LAYOUT, IdentValue.AUTO);
    }
    
    protected boolean isSkipWhenCollapsingMargins() {
        return true;
    } 

    // The following rules apply for resolving conflicts and figuring out which
    // border
    // to use.
    // (1) Borders with the 'border-style' of 'hidden' take precedence over all
    // other conflicting
    // borders. Any border with this value suppresses all borders at this
    // location.
    // (2) Borders with a style of 'none' have the lowest priority. Only if the
    // border properties of all
    // the elements meeting at this edge are 'none' will the border be omitted
    // (but note that 'none' is
    // the default value for the border style.)
    // (3) If none of the styles are 'hidden' and at least one of them is not
    // 'none', then narrow borders
    // are discarded in favor of wider ones. If several have the same
    // 'border-width' then styles are preferred
    // in this order: 'double', 'solid', 'dashed', 'dotted', 'ridge', 'outset',
    // 'groove', and the lowest: 'inset'.
    // (4) If border styles differ only in color, then a style set on a cell
    // wins over one on a row,
    // which wins over a row group, column, column group and, lastly, table. It
    // is undefined which color
    // is used when two elements of the same type disagree.
    public static CollapsedBorderValue compareBorders(
            CollapsedBorderValue border1, CollapsedBorderValue border2, boolean returnNullOnEqual) {
        // Sanity check the values passed in.  If either is null, return the other.
        if (!border2.exists()) {
            return border1;
        }
        
        if (!border1.exists()) {
            return border2;
        }
        
        // Rule #1 above.
        if (border1.style() == IdentValue.HIDDEN || border2.style() == IdentValue.HIDDEN) {
            return CollapsedBorderValue.NO_BORDER; // No border should exist at
                                                  // this location.
        }

        // Rule #2 above. A style of 'none' has lowest priority and always loses
        // to any other border.
        if (border2.style() == IdentValue.NONE) {
            return border1;
        }

        if (border1.style() == IdentValue.NONE) {
            return border2;
        }

        // The first part of rule #3 above. Wider borders win.
        if (border1.width() != border2.width()) {
            return border1.width() > border2.width() ? border1 : border2;
        }

        // The borders have equal width. Sort by border style.
        if (border1.style() != border2.style()) {
            return BORDER_PRIORITIES[border1.style().FS_ID] > 
                BORDER_PRIORITIES[border2.style().FS_ID] ? border1 : border2;
        }

        // The border have the same width and style. Rely on precedence (cell
        // over row over row group, etc.)
        if (returnNullOnEqual && border1.precedence() == border2.precedence()) {
            return null;
        } else {
            return border1.precedence() >= border2.precedence() ? border1 : border2;    
        } 
    }
    
    private static CollapsedBorderValue compareBorders(
            CollapsedBorderValue border1, CollapsedBorderValue border2) {
        return compareBorders(border1, border2, false);
    }
    
    private CollapsedBorderValue collapsedLeftBorder(CssContext c) {
        BorderPropertySet border = getStyle().getBorder(c);
        // For border left, we need to check, in order of precedence:
        // (1) Our left border.
        CollapsedBorderValue result = CollapsedBorderValue.borderLeft(border, BCELL);

        // (2) The previous cell's right border.
        TableCellBox prevCell = getTable().cellLeft(this);
        if (prevCell != null) {
            result = compareBorders(
                    result, CollapsedBorderValue.borderRight(prevCell.getStyle().getBorder(c), BCELL));
            if (!result.exists()) {
                return result;
            }
        } else if (getCol() == 0) {
            // (3) Our row's left border.
            result = compareBorders(
                    result, CollapsedBorderValue.borderLeft(getParent().getStyle().getBorder(c), BROW));
            if (!result.exists()) {
                return result;
            }

            // (4) Our row group's left border.
            result = compareBorders(
                    result, CollapsedBorderValue.borderLeft(getSection().getStyle().getBorder(c), BROWGROUP));
            if (!result.exists()) {
                return result;
            }
        }

        // (5) Our column's left border.
        TableColumn colElt = getTable().colElement(getCol());
        if (colElt != null) {
            result = compareBorders(
                    result, CollapsedBorderValue.borderLeft(colElt.getStyle().getBorder(c), BCOL));
            if (!result.exists()) {
                return result;
            }
        }

        // (6) The previous column's right border.
        if (getCol() > 0) {
            colElt = getTable().colElement(getCol() - 1);
            if (colElt != null) {
                result = compareBorders(
                        result, CollapsedBorderValue.borderRight(colElt.getStyle().getBorder(c), BCOL));
                if (!result.exists()) {
                    return result;
                }
            }
        }

        if (getCol() == 0) {
            // (7) The table's left border.
            result = compareBorders(
                    result, CollapsedBorderValue.borderLeft(getTable().getStyle().getBorder(c), BTABLE));
            if (!result.exists()) {
                return result;
            }
        }

        return result;
    }
    
    private CollapsedBorderValue collapsedRightBorder(CssContext c) {
        TableBox tableElt = getTable();
        boolean inLastColumn = false;
        int effCol = tableElt.colToEffCol(getCol() + getStyle().getColSpan() - 1);
        if (effCol == tableElt.numEffCols() - 1) {
            inLastColumn = true;
        }

        // For border right, we need to check, in order of precedence:
        // (1) Our right border.
        CollapsedBorderValue result = 
            CollapsedBorderValue.borderRight(getStyle().getBorder(c), BCELL);

        // (2) The next cell's left border.
        if (!inLastColumn) {
            TableCellBox nextCell = tableElt.cellRight(this);
            if (nextCell != null) {
                result = compareBorders(result, 
                        CollapsedBorderValue.borderLeft(nextCell.getStyle().getBorder(c), BCELL));
                if (!result.exists()) {
                    return result;
                }
            }
        } else {
            // (3) Our row's right border.
            result = compareBorders(result, 
                    CollapsedBorderValue.borderRight(getParent().getStyle().getBorder(c), BROW));
            if (!result.exists()) {
                return result;
            }

            // (4) Our row group's right border.
            result = compareBorders(result, 
                    CollapsedBorderValue.borderRight(getSection().getStyle().getBorder(c), BROWGROUP));
            if (!result.exists()) {
                return result;
            }
        }

        // (5) Our column's right border.
        TableColumn colElt = getTable().colElement(getCol() + getStyle().getColSpan() - 1);
        if (colElt != null) {
            result = compareBorders(result, 
                    CollapsedBorderValue.borderRight(colElt.getStyle().getBorder(c), BCOL));
            if (!result.exists()) {
                return result;
            }
        }

        // (6) The next column's left border.
        if (!inLastColumn) {
            colElt = tableElt.colElement(getCol() + getStyle().getColSpan());
            if (colElt != null) {
                result = compareBorders(result, 
                        CollapsedBorderValue.borderLeft(colElt.getStyle().getBorder(c), BCOL));
                if (!result.exists()) {
                    return result;
                }
            }
        } else {
            // (7) The table's right border.
            result = compareBorders(result, 
                    CollapsedBorderValue.borderRight(tableElt.getStyle().getBorder(c), BTABLE));
            if (!result.exists()) {
                return result;
            }
        }

        return result;
    }
    
    private CollapsedBorderValue collapsedTopBorder(CssContext c) {
        // For border top, we need to check, in order of precedence:
        // (1) Our top border.
        CollapsedBorderValue result = 
            CollapsedBorderValue.borderTop(getStyle().getBorder(c), BCELL);

        TableCellBox prevCell = getTable().cellAbove(this);
        if (prevCell != null) {
            // (2) A previous cell's bottom border.
            result = compareBorders(result, 
                        CollapsedBorderValue.borderBottom(prevCell.getStyle().getBorder(c), BCELL));
            if (!result.exists()) {
                return result;
            }
        }

        // (3) Our row's top border.
        result = compareBorders(result, 
                    CollapsedBorderValue.borderTop(getParent().getStyle().getBorder(c), BROW));
        if (!result.exists()) {
            return result;
        }

        // (4) The previous row's bottom border.
        if (prevCell != null) {
            TableRowBox prevRow = null;
            if (prevCell.getSection() == getSection()) {
                prevRow = (TableRowBox) getParent().getPreviousSibling();
            } else {
                prevRow = prevCell.getSection().getLastRow();
            }

            if (prevRow != null) {
                result = compareBorders(result, 
                            CollapsedBorderValue.borderBottom(prevRow.getStyle().getBorder(c), BROW));
                if (!result.exists()) {
                    return result;
                }
            }
        }

        // Now check row groups.
        TableSectionBox currSection = getSection();
        if (getRow() == 0) {
            // (5) Our row group's top border.
            result = compareBorders(result, 
                        CollapsedBorderValue.borderTop(currSection.getStyle().getBorder(c), BROWGROUP));
            if (!result.exists()) {
                return result;
            }

            // (6) Previous row group's bottom border.
            currSection = getTable().sectionAbove(currSection, false);
            if (currSection != null) {
                result = compareBorders(result, 
                            CollapsedBorderValue.borderBottom(currSection.getStyle().getBorder(c), BROWGROUP));
                if (!result.exists()) {
                    return result;
                }
            }
        }

        if (currSection == null) {
            // (8) Our column's top border.
            TableColumn colElt = getTable().colElement(getCol());
            if (colElt != null) {
                result = compareBorders(result, 
                            CollapsedBorderValue.borderTop(colElt.getStyle().getBorder(c), BCOL));
                if (!result.exists()) {
                    return result;
                }
            }

            // (9) The table's top border.
            result = compareBorders(result, 
                        CollapsedBorderValue.borderTop(getTable().getStyle().getBorder(c), BTABLE));
            if (!result.exists()) {
                return result;
            }
        }

        return result;
    }

    private CollapsedBorderValue collapsedBottomBorder(CssContext c) {
        // For border top, we need to check, in order of precedence:
        // (1) Our bottom border.
        CollapsedBorderValue result = 
            CollapsedBorderValue.borderBottom(getStyle().getBorder(c), BCELL);

        TableCellBox nextCell = getTable().cellBelow(this);
        if (nextCell != null) {
            // (2) A following cell's top border.
            result = compareBorders(result, 
                        CollapsedBorderValue.borderTop(nextCell.getStyle().getBorder(c), BCELL));
            if (!result.exists()) {
                return result;
            }
        }

        // (3) Our row's bottom border. (FIXME: Deal with rowspan!)
        result = compareBorders(result, 
                    CollapsedBorderValue.borderBottom(getParent().getStyle().getBorder(c), BROW));
        if (!result.exists()) {
            return result;
        }

        // (4) The next row's top border.
        if (nextCell != null) {
            result = compareBorders(result, 
                        CollapsedBorderValue.borderTop(nextCell.getParent().getStyle().getBorder(c), BROW));
            if (!result.exists()) {
                return result;
            }
        }

        // Now check row groups.
        TableSectionBox currSection = getSection();
        if (getRow() + getStyle().getRowSpan() >= currSection.numRows()) {
            // (5) Our row group's bottom border.
            result = compareBorders(result, 
                        CollapsedBorderValue.borderBottom(currSection.getStyle().getBorder(c), BROWGROUP));
            if (!result.exists()) {
                return result;
            }

            // (6) Following row group's top border.
            currSection = getTable().sectionBelow(currSection, false);
            if (currSection != null) {
                result = compareBorders(result, 
                            CollapsedBorderValue.borderTop(currSection.getStyle().getBorder(c), BROWGROUP));
                if (!result.exists()) {
                    return result;
                }
            }
        }

        if (currSection == null) {
            // (8) Our column's bottom border.
            TableColumn colElt = getTable().colElement(getCol());
            if (colElt != null) {
                result = compareBorders(result, 
                            CollapsedBorderValue.borderBottom(colElt.getStyle().getBorder(c), BCOL));
                if (!result.exists()) {
                    return result;
                }
            }

            // (9) The table's bottom border.
            result = compareBorders(result, 
                        CollapsedBorderValue.borderBottom(getTable().getStyle().getBorder(c), BTABLE));
            if (!result.exists()) {
                return result;
            }
        }

        return result;
    }
    
    private Rectangle getCollapsedBorderBounds(CssContext c) {
        BorderPropertySet border = getCollapsedPaintingBorder();
        Rectangle bounds = getPaintingBorderEdge(c);
        bounds.x -= (int) border.left() / 2;
        bounds.y -= (int) border.top() / 2;
        bounds.width += (int) border.left() / 2 + ((int) border.right() + 1) / 2;
        bounds.height += (int) border.top() / 2 + ((int) border.bottom() + 1) / 2;
        
        return bounds;
    }
    
    public Rectangle getPaintingClipEdge(CssContext c) {
        if (hasCollapsedPaintingBorder()) {
            return getCollapsedBorderBounds(c);
        } else {
            return super.getPaintingClipEdge(c);
        }
    }
    
    public boolean hasCollapsedPaintingBorder() {
        return _collapsedPaintingBorder != null;
    }
    
    protected BorderPropertySet getCollapsedPaintingBorder() {
        return _collapsedPaintingBorder;
    }

    public CollapsedBorderValue getCollapsedBorderBottom() {
        return _collapsedBorderBottom;
    }

    public CollapsedBorderValue getCollapsedBorderLeft() {
        return _collapsedBorderLeft;
    }

    public CollapsedBorderValue getCollapsedBorderRight() {
        return _collapsedBorderRight;
    }

    public CollapsedBorderValue getCollapsedBorderTop() {
        return _collapsedBorderTop;
    }
    
    public void addCollapsedBorders(Set all, List borders) {
        if (_collapsedBorderTop.exists() && !all.contains(_collapsedBorderTop)) {
            all.add(_collapsedBorderTop);
            borders.add(new CollapsedBorderSide(this, BorderPainter.TOP));
        }
        
        if (_collapsedBorderRight.exists() && !all.contains(_collapsedBorderRight)) {
            all.add(_collapsedBorderRight);
            borders.add(new CollapsedBorderSide(this, BorderPainter.RIGHT));
        }
        
        if (_collapsedBorderBottom.exists() && !all.contains(_collapsedBorderBottom)) {
            all.add(_collapsedBorderBottom);
            borders.add(new CollapsedBorderSide(this, BorderPainter.BOTTOM));
        }
        
        if (_collapsedBorderLeft.exists() && !all.contains(_collapsedBorderLeft)) {
            all.add(_collapsedBorderLeft);
            borders.add(new CollapsedBorderSide(this, BorderPainter.LEFT));
        }
    }
}