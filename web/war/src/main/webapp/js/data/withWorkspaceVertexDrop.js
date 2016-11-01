
define([], function() {
    'use strict';

    return withWorkspaceVertexDrop;

    function withWorkspaceVertexDrop() {

        this.after('initialize', function() {
            var self = this;
            this.on('applicationReady currentUserVisalloDataUpdated', _.once(function() {
                var enabled = false,
                    droppable = $(document.body);

                // Prevent dragging any context menu items to graph/map
                $(document.body).on('dragstart', '.dropdown-menu', function(e) {
                    e.preventDefault();
                });

                // Other droppables might be on top of graph, listen to
                // their over/out events and ignore drops if the user hasn't
                // dragged outside of them. Can't use greedy option since they are
                // absolutely positioned
                $(document.body).on('dropover dropout', function(e, ui) {
                    var target = $(e.target),
                        appDroppable = target.is(droppable),
                        parentDroppables = target.parents('.ui-droppable');

                    if (appDroppable) {
                        enabled = true;
                        return;
                    }

                    // If this droppable has no parent droppables
                    if (parentDroppables.length === 1 && parentDroppables.is(droppable)) {
                        enabled = e.type === 'dropout';
                    }
                });

                require(['jquery-ui'], function() {

                    var stopDroppable = function(event, ui) {
                        $('.draggable-wrapper').remove();

                        // Early exit if should leave to a different droppable
                        if (!enabled) return;

                        verticesFromDraggable(ui.draggable, self.dataRequestPromise)
                            .done(function(vertices) {
                                var graphVisible = $('.graph-pane-2d').is('.visible');

                                if (visalloData.currentWorkspaceEditable && vertices.length) {
                                    self.trigger('clearWorkspaceFilter');
                                    self.trigger('verticesDropped', {
                                        vertices: vertices,
                                        dropPosition: { x: event.clientX, y: event.clientY }
                                    });
                                }
                            })
                    };

                    droppable.droppable({
                        tolerance: 'pointer',
                        accept: function(item) {
                            return true;
                        },
                        over: function(event, ui) {
                            var draggable = ui.draggable,
                                start = true,
                                graphVisible = $('.graph-pane-2d').is('.visible'),
                                dashboardVisible = $('.dashboard-pane').is('.visible'),
                                vertices,
                                started = false,
                                wrapper = $('.draggable-wrapper');

                            // Prevent map from swallowing mousemove events by adding
                            // this transparent full screen div
                            if (wrapper.length === 0) {
                                wrapper = $('<div class="draggable-wrapper"/>').appendTo(document.body);
                            }

                            draggable.off('drag.droppable-tracking');
                            draggable.on('drag.droppable-tracking', function handler(event, draggableUI) {
                                if (!vertices) {
                                    if (!started) {
                                        started = true;
                                        // TODO: for non-cached vertices we need
                                        // some ui feedback that it's loading
                                        verticesFromDraggable(draggable, self.dataRequestPromise)
                                            .done(function(v) {
                                                if (!v.length) return;
                                                vertices = v;
                                                handler(event, draggableUI);
                                            })
                                    }
                                    return;
                                }

                                if (graphVisible) {
                                    ui.helper.toggleClass('draggable-invisible', enabled);
                                } else if (dashboardVisible) {
                                    $(event.target).closest('.dialog-popover').data('preventTeardown', true);

                                    var count = 0;
                                    self.on(document, 'didToggleDisplay', function didToggle(event, data) {
                                        count++;
                                        if (count >= 2) {
                                            self.off(document, 'didToggleDisplay', didToggle);
                                            dashboardVisible = false;
                                            graphVisible = true;
                                            handler(event, draggableUI);
                                        }
                                    })
                                    self.trigger('menubarToggleDisplay', { name: 'graph' });
                                    return;
                                }

                                self.trigger('toggleWorkspaceFilter', { enabled: !enabled });
                                if (graphVisible) {
                                    if (enabled) {
                                        self.trigger('verticesHovering', {
                                            vertices: vertices,
                                            start: start,
                                            position: { x: event.pageX, y: event.pageY }
                                        });
                                        start = false;
                                    } else {
                                        self.trigger('verticesHoveringEnded', { vertices: vertices });
                                    }
                                }
                            });
                        },
                        deactivate: stopDroppable,
                        drop: stopDroppable
                    });
                });
            }));

            function verticesFromDraggable(draggable, dataRequestPromise) {
                var draggableData = draggable.data('ui-draggable');
                if (!draggableData) return Promise.resolve([]);

                var alsoDragging = draggableData.alsoDragging,
                    anchors = draggable;

                if (alsoDragging && alsoDragging.length) {
                    anchors = draggable.add(alsoDragging.map(function(i, a) {
                        return a.data('original');
                    }));
                }

                var vertexIds = _.compact(anchors.map(function(i, a) {
                    a = $(a);
                    var vertexId = a.data('vertexId') || a.closest('li').data('vertexId');
                    if (a.is('.facebox')) return;

                    if (!vertexId) {

                        // Highlighted entities (legacy info)
                        var info = a.data('info') || a.closest('li').data('info');

                        vertexId = info && (info.resolvedToVertexId || info.graphVertexId || info.id);

                        // Detected objects
                        if (info && info.entityVertex) {
                            self.updateCacheWithVertex(info.entityVertex);
                            vertexId = info.entityVertex.id;
                        }

                        if (!vertexId) {
                            return;
                        }
                    }
                    return vertexId;
                }).toArray());

                return dataRequestPromise.then(function(dataRequest) {
                    return dataRequest('vertex', 'store', { vertexIds: vertexIds });
                });
            }
        })
    }
});
