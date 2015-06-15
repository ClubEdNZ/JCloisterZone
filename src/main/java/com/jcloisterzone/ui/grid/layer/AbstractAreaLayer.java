package com.jcloisterzone.ui.grid.layer;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.ImageIcon;

import com.jcloisterzone.Player;
import com.jcloisterzone.board.Location;
import com.jcloisterzone.board.Position;
import com.jcloisterzone.board.Tile;
import com.jcloisterzone.config.Config.DebugConfig;
import com.jcloisterzone.figure.SmallFollower;
import com.jcloisterzone.ui.GameController;
import com.jcloisterzone.ui.ImmutablePoint;
import com.jcloisterzone.ui.grid.GridMouseAdapter;
import com.jcloisterzone.ui.grid.GridMouseListener;
import com.jcloisterzone.ui.grid.GridPanel;
import com.jcloisterzone.ui.resources.FeatureArea;


public abstract class AbstractAreaLayer extends AbstractGridLayer implements GridMouseListener {

    private static final AlphaComposite AREA_ALPHA_COMPOSITE = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .6f);
    private static final AlphaComposite FIGURE_HIGHLIGHT_AREA_ALPHA_COMPOSITE = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .75f);

    private Player player;
    private boolean active;
    private Map<Location, FeatureArea> areas;
    private FeatureArea selectedArea;
    private Position selectedPosition;

    /*if true, area is displayed as placed meeple
     this method is intended for tile placement debugging and is not optimized for performace
     */
    private boolean figureHighlight = false;

    public AbstractAreaLayer(GridPanel gridPanel, GameController gc) {
        super(gridPanel, gc);
        DebugConfig debugConfig = getClient().getConfig().getDebug();
        if (debugConfig != null && "figure".equals(debugConfig.getArea_highlight())) {
            figureHighlight = true;
        }
    }


    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void onShow() {
        super.onShow();
        //TODO should ne based on event player
        player = getGame().getActivePlayer();
    }

    @Override
    public void onHide() {
        super.onHide();
        player = null;
        cleanAreas();
    }

    private class MoveTrackingGridMouseAdapter extends GridMouseAdapter {

        public MoveTrackingGridMouseAdapter(GridPanel gridPanel, GridMouseListener listener) {
            super(gridPanel, listener);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            super.mouseMoved(e);
            if (areas == null) return;
            int size = getSquareSize();
            Point2D point = gridPanel.getRelativePoint(e.getPoint());
            int x = (int) point.getX();
            int y = (int) point.getY();
            if (x < 0) x += 1000 * size; //prevent mod from negative number
            if (y < 0) y += 1000 * size; //prevent mod from negative number
            x = x % size;
            y = y % size;
            FeatureArea swap = null;
            for (Entry<Location, FeatureArea> entry : areas.entrySet()) {
                FeatureArea fa = entry.getValue();
                if (fa.getArea().contains(x, y)) {
                    if (swap == null) {
                        swap = fa;
                    } else {
                        if (swap.getzIndex() == fa.getzIndex()) {
                            // two overlapping areas at same point with same zIndex - select no one
                            swap = null;
                            break;
                        } else if (fa.getzIndex() > swap.getzIndex()) {
                           swap = fa;
                        } //else do nothing
                    }
                }
            }
            Location l1 = swap == null ? null : swap.getLoc();
            Location l2 = selectedArea == null ? null : selectedArea.getLoc();
            if (l1 != l2) {
                selectedArea = swap;
                gridPanel.repaint();
            }
        }

    }

    @Override
    protected GridMouseAdapter createGridMouserAdapter(GridMouseListener listener) {
        return new MoveTrackingGridMouseAdapter(gridPanel, listener);
    }

    private void cleanAreas() {
        areas = null;
        selectedPosition = null;
        selectedArea = null;
    }

    @Override
    public void zoomChanged(int squareSize) {
        Position prevSelectedPosition = selectedPosition;
        super.zoomChanged(squareSize);
        if (selectedPosition != null && selectedPosition.equals(prevSelectedPosition)) {
            //no square enter/leave trigger in this case - refresh areas
            areas = prepareAreas(gridPanel.getTile(selectedPosition), selectedPosition);
        }
    }

    @Override
    public void squareEntered(MouseEvent e, Position p) {
        Tile tile = gridPanel.getTile(p);
        if (tile != null) {
            selectedPosition = p;
            areas = prepareAreas(tile, p);
        }
    }

    protected abstract Map<Location, FeatureArea> prepareAreas(Tile tile, Position p);


    @Override
    public void squareExited(MouseEvent e, Position p) {
        if (selectedPosition != null) {
            cleanAreas();
            gridPanel.repaint();
        }
    }

    protected abstract void performAction(Position pos, Location selected);

    @Override
    public void mouseClicked(MouseEvent e, Position pos) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (selectedArea != null) {
                performAction(pos, selectedArea.getLoc());
                e.consume();
            }
        }
    }

    @Override
    public void paint(Graphics2D g2) {
        if (selectedArea != null && areas != null) {
            Composite old = g2.getComposite();
            if (figureHighlight) {
                paintFigureHighlight(g2);
            } else {
                paintAreaHighlight(g2);
            }
            g2.setComposite(old);
        }
    }

    /** debug purposes highlight - it always shows basic follower (doesn't important for dbg */
    private void paintFigureHighlight(Graphics2D g2) {
        //ugly copy pasted code from Meeple but uncached here
        g2.setComposite(FIGURE_HIGHLIGHT_AREA_ALPHA_COMPOSITE);
        Tile tile = getGame().getBoard().get(selectedPosition);
        ImmutablePoint point = getClient().getResourceManager().getMeeplePlacement(tile, SmallFollower.class, selectedArea.getLoc());
        Player p = getGame().getActivePlayer();
        Image unscaled = getClient().getFigureTheme().getFigureImage(SmallFollower.class, p.getColors().getMeepleColor(), null);
        int size = (int) (getSquareSize() * MeepleLayer.FIGURE_SIZE_RATIO);
        Image scaled = unscaled.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        scaled = new ImageIcon(scaled).getImage();
        ImmutablePoint scaledOffset = point.scale(getSquareSize(), (int)(getSquareSize() * MeepleLayer.FIGURE_SIZE_RATIO));
        g2.drawImage(scaled, getOffsetX(selectedPosition) + scaledOffset.getX(), getOffsetY(selectedPosition) + scaledOffset.getY(), gridPanel);
    }

    /** standard highlight **/
    private void paintAreaHighlight(Graphics2D g2) {
        Player p = getGame().getActivePlayer();
        if (p != null && p.equals(player)) { //sync issue
            g2.setColor(p.getColors().getMeepleColor());
            g2.setComposite(AREA_ALPHA_COMPOSITE);
            g2.fill(transformArea(selectedArea.getArea(), selectedPosition));
        }
    }
}
