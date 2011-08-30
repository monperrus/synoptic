package synopticgwt.client.invariants;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gwt.core.client.JavaScriptObject;

import synopticgwt.client.util.JsniUtil;
import synopticgwt.shared.GWTInvariant;
import synopticgwt.shared.GWTInvariantSet;

/**
 * Used to create an invariants graphic in which an invariant inv(x,y) is
 * represented as a line between two vertices x and y. The graph is tripartite
 * graph (three sets of vertices, with no edges between vertices in the same
 * set). The sets have identical sizes and contain the same kinds of vertices --
 * a vertex corresponding to each event type that is part of at least one
 * invariant.
 */
public class InvariantsGraph {

    /**
     * Creates the invariant graphic corresponding to gwtInvs in a DIV with id
     * indicated by invCanvasId.
     */
    public static void createInvariantsGraphic(GWTInvariantSet gwtInvs,
            String invCanvasId) {
        JavaScriptObject eventTypesJS = JavaScriptObject.createArray();
        JavaScriptObject AFbyJS = JavaScriptObject.createArray();
        JavaScriptObject NFbyJS = JavaScriptObject.createArray();
        JavaScriptObject APJS = JavaScriptObject.createArray();

        Set<String> invTypes = gwtInvs.getInvTypes();
        int eTypesCnt = 0;

        Set<String> eventTypes = new LinkedHashSet<String>();
        int longestEType = 0;

        // Iterate through all invariants to create the JS objects for drawing
        // the invariants graphic.
        for (String invType : invTypes) {
            final List<GWTInvariant> invs = gwtInvs.getInvs(invType);

            for (GWTInvariant inv : invs) {
                if (!eventTypes.contains(inv.getSource())) {
                    JsniUtil.pushArray(eventTypesJS, inv.getSource());
                    eventTypes.add(inv.getSource());
                    if (inv.getSource().length() > longestEType) {
                        longestEType = inv.getSource().length();
                    }
                    eTypesCnt++;
                }
                if (!eventTypes.contains(inv.getTarget())) {
                    JsniUtil.pushArray(eventTypesJS, inv.getTarget());
                    eventTypes.add(inv.getTarget());
                    if (inv.getTarget().length() > longestEType) {
                        longestEType = inv.getTarget().length();
                    }
                    eTypesCnt++;
                }

                String x = inv.getSource();
                String y = inv.getTarget();
                if (invType.equals("AFby")) {
                    JsniUtil.addToKeyInArray(AFbyJS, x, y);
                } else if (invType.equals("NFby")) {
                    JsniUtil.addToKeyInArray(NFbyJS, x, y);
                } else if (invType.equals("AP")) {
                    JsniUtil.addToKeyInArray(APJS, x, y);
                }
            }
        }

        // A little magic to size things right.
        int lX = (longestEType * 30) / 2 - 60;
        int mX = lX + (longestEType * 30);
        int rX = mX + (longestEType * 30);
        int width = rX + 50;

        // Pass the created JavaScript structures to the native call that will
        // create the graphic.
        InvariantsGraph.createInvariantsGraphic(AFbyJS, NFbyJS, APJS,
                eventTypesJS, width, (eTypesCnt + 1) * 50, lX, mX, rX,
                invCanvasId);
    }

    // //////////////////////////////////////////////////////////////////////////
    // JSNI methods -- JavaScript Native Interface methods. The method body of
    // these calls is pure JavaScript.

    /**
     * A JSNI method to create and display an invariants graphic.
     * 
     * @param AFby
     *            associative array with AFby relations
     * @param NFby
     *            associative array with NFby relations
     * @param AP
     *            associative array with AP relations
     * @param eTypes
     *            array of all event types
     * @param width
     *            width of graphic
     * @param height
     *            height of graphic
     * @param lX
     *            the x value of the left most column
     * @param mX
     *            the x value of the middle column
     * @param rX
     *            the x value of the right most column
     * @param canvasId
     *            the div id where to draw the graphic
     */
    private static native void createInvariantsGraphic(JavaScriptObject AFby,
            JavaScriptObject NFby, JavaScriptObject AP,
            JavaScriptObject eTypes, int width, int height, int lX, int mX,
            int rX, String canvasId) /*-{

		var paper = $wnd.Raphael($doc.getElementById(canvasId), width, height);

		// Attribute to track the target node pointed to from the middle text-element.
		paper.customAttributes.dest = function(textElem) {
			return {
				dest : textElem
			};
		};

		// Attribute to track the highlighted color of the lines connected to the selected middle text-element.
		paper.customAttributes.highlight = function(color) {
			return {
				highlight : color
			};
		};

		var topMargin = 20;
		var dY = 50;

		var lines = new Array();

		// These will contain text labels in the middle/right/left columns:
		var tMiddlesArr = [];
		var tRightsArr = [];
		var tLeftsArr = [];

		var ypos = new Array();

		// Create the three columns of text labels.
		for ( var i = 0; i < eTypes.length; i++) {
			var eType = eTypes[i]

			var tMiddle = paper.text(mX, dY * i + topMargin, eType);
			tMiddlesArr.push(tMiddle);
			tMiddle.attr({
				'font-size' : "30px",
				fill : "grey"
			});

			// Remember the y position of every row of labels.
			ypos[eType] = dY * i + 10;

			// Do not create the INITIAL labels on the left/right
			if (eType == "INITIAL") {
				continue;
			}

			var tLeft = paper.text(lX, dY * i + topMargin, eType);
			tLeft.attr({
				'font-size' : "30px",
				fill : "grey"
			});
			tLeftsArr[eType] = tLeft;

			var tRight = paper.text(rX, dY * i + topMargin, eType);
			tRight.attr({
				'font-size' : "30px",
				fill : "grey"
			});
			tRightsArr[eType] = tRight;
		}

		// Create all the lines by iterating through labels in the middle column.
		for ( var i = 0; i < eTypes.length; i++) {
			var eType = eTypes[i]
			lines[eType] = []
		}

		for ( var i = 0; i < eTypes.length; i++) {
			var eType = eTypes[i]
			var tMiddle = tMiddlesArr[i];

			// AP:
			for ( var j in AP[eType]) {
				var line = paper.path(("M" + mX + " " + ypos[AP[eType][j]]
						+ "L" + lX + " " + ypos[eType]));
				line.attr({
					stroke : "grey",
					highlight : "blue",
					dest : tLeftsArr[eType]
				});
				// NOTE: we associate the middle label destination of the arrow, not the left label source.
				lines[AP[eType][j]].push(line);
			}

			// AFby:
			for ( var j in AFby[eType]) {
				var line = paper.path(("M" + mX + " " + ypos[eType] + "L" + rX
						+ " " + ypos[AFby[eType][j]]));
				line.attr({
					stroke : "grey",
					highlight : "blue",
					dest : tRightsArr[AFby[eType][j]]
				});
				lines[eType].push(line);
			}

			// NFby:
			for ( var j in NFby[eType]) {
				var line = paper.path(("M" + mX + " " + ypos[eType] + "L" + rX
						+ " " + ypos[NFby[eType][j]]));
				line.attr({
					stroke : "grey",
					highlight : "red",
					dest : tRightsArr[NFby[eType][j]]
				});
				lines[eType].push(line);
			}

			// Function to execute when the tMiddle label is pointed-to.
			tMiddle.mouseover(function(y) {
				return function(e) {
					// y is tMiddle
					for ( var line in lines[y.attr('text')]) {
						lines[y.attr('text')][line].attr({
							'stroke-width' : '3'
						});
						lines[y.attr('text')][line].attr({
							stroke : lines[y.attr('text')][line]
									.attr('highlight')
						});
						lines[y.attr('text')][line].attr('dest').attr({
							fill : "black"
						});
					}
					y.attr({
						fill : "black"
					});

				};
			}(tMiddle));

			// Function to execute when the tMiddle label is not pointed-to.
			tMiddle.mouseout(function(y) {
				return function(e) {
					for ( var line in lines[y.attr('text')]) {
						lines[y.attr('text')][line].attr({
							'stroke-width' : '1'
						});
						lines[y.attr('text')][line].attr({
							stroke : "grey"
						});
						lines[y.attr('text')][line].attr('dest').attr({
							fill : "grey"
						});
					}
					y.attr({
						fill : "grey"
					});
				};
			}(tMiddle));
		}
    }-*/;

    // </JSNI methods>
    // //////////////////////////////////////////////////////////////////////////
}