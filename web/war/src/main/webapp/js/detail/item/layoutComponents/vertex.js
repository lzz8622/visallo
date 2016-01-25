define([
    'detail/toolbar/toolbar',
    'util/vertex/formatters'
], function(Toolbar, F) {
    'use strict';

    var conceptDisplay = _.compose(_.property('displayName'), F.vertex.concept),
        vertexDisplay = F.vertex.title;

    return [
        {
            applyTo: { type: 'vertex' },
            identifier: 'org.visallo.layout.root',
            layout: { type: 'flex', options: { direction: 'column' }},
            componentPath: 'detail/item/vertex',
            children: [
                { ref: 'org.visallo.layout.header' },
                { ref: 'org.visallo.layout.body', style: { flex: 1, overflow: 'auto' } }
            ]
        },
        {
            applyTo: { displayType: 'video' },
            identifier: 'org.visallo.layout.body',
            children: [
                { componentPath: 'detail/video/video', className: 'org-visallo-video' },
                { componentPath: 'detail/properties/properties', className: 'org-visallo-properties', modelAttribute: 'data' },
                { componentPath: 'comments/comments', className: 'org.visallo-comments', modelAttribute: 'data' },
                { componentPath: 'detail/relationships/relationships', className: 'org-visallo-relationships', modelAttribute: 'data' },
                { componentPath: 'detail/text/text', className: 'org-visallo-texts' }
            ]
        },
        {
            applyTo: { displayType: 'image' },
            identifier: 'org.visallo.layout.body',
            children: [
                { componentPath: 'detail/image/image' },
                { componentPath: 'detail/detectedObjects/detectedObjects' },
                { componentPath: 'detail/properties/properties', className: 'org-visallo-properties', modelAttribute: 'data' },
                { componentPath: 'comments/comments', className: 'org.visallo-comments', modelAttribute: 'data' },
                { componentPath: 'detail/relationships/relationships', className: 'org-visallo-relationships', modelAttribute: 'data' },
                { componentPath: 'detail/text/text', className: 'org-visallo-texts' }
            ]
        },
        {
            applyTo: { displayType: 'audio' },
            identifier: 'org.visallo.layout.body',
            children: [
                { componentPath: 'detail/audio/audio' },
                { componentPath: 'detail/properties/properties', className: 'org-visallo-properties', modelAttribute: 'data' },
                { componentPath: 'comments/comments', className: 'org.visallo-comments', modelAttribute: 'data' },
                { componentPath: 'detail/relationships/relationships', className: 'org-visallo-relationships', modelAttribute: 'data' },
                { componentPath: 'detail/text/text', className: 'org-visallo-texts' }
            ]
        },
        {
            applyTo: { type: 'vertex' },
            identifier: 'org.visallo.layout.header.text',
            layout: { type: 'flex', options: { direction: 'column' }},
            className: 'vertex-header',
            children: [
                { componentPath: 'detail/headerImage/image', className: 'entity-glyphicon', modelAttribute: 'data' },
                { ref: 'org.visallo.layout.text', style: 'title', model: vertexDisplay },
                { ref: 'org.visallo.layout.text', style: 'subtitle', model: conceptDisplay }
            ]
        }
    ]
});
