define([
    'flight/lib/component',
    './properties.hbs',
    'util/service/propertiesPromise',
    './withSelect'
], function(
    defineComponent,
    template,
    config,
    withSelect) {
    'use strict';

    var HIDE_PROPERTIES = ['http://visallo.org/comment#entry'];

    return defineComponent(PropertySelect, withSelect);

    function PropertySelect() {

        var PLACEHOLDER = i18n('field.selection.placeholder');

        this.defaultAttrs({
            findPropertySelection: 'input',
            showAdminProperties: false,
            rollupCompound: true,
            maxItems: withSelect.maxItemsFromConfiguration('typeahead.properties.maxItems')
        });

        this.after('initialize', function() {
            var self = this;

            this.on('filterProperties', this.onFilterProperties);

            if (this.attr.selectedProperty) {
                this.currentProperty = this.attr.selectedProperty;
            }

            this.$node.html(template({
                placeholder: this.attr.placeholder,
                selected: this.currentProperty && this.currentProperty.displayName || ''
            }));

            if (this.attr.properties.length === 0 || this.attr.properties.length.value === 0) {
                this.select('findPropertySelection')
                    .attr('placeholder', i18n('field.selection.no_valid'))
                    .attr('disabled', true);
            } else {
                this.queryPropertyMap = {};

                var field = this.select('findPropertySelection')
                    .on('focus', function(e) {
                        var target = $(e.target);
                        target.attr('placeholder', PLACEHOLDER)
                    })
                    .on('click', function(e) {
                        var target = $(e.target);

                        if (target.val()) {
                            target.typeahead('lookup').select();
                        } else {
                            target.typeahead('lookup');
                        }

                        target.attr('placeholder', PLACEHOLDER);
                    })
                    .on('change blur', function(e) {
                        var target = $(e.target);
                        if (self.currentProperty) {
                            target.val(self.currentProperty.displayName || self.currentProperty.title);
                        } else {
                            target.val('');
                        }
                        target.attr('placeholder', self.attr.placeholder);
                    })
                    .typeahead({
                        minLength: 0,
                        items: this.attr.maxItems,
                        source: function() {
                            var sourceProperties = self.filterSourceProperties(self.propertiesForSource);

                            return _.chain(sourceProperties)
                                .map(mapProperty)
                                .sortBy(sortByPropertyGroupAndDisplayName)
                                .value();

                            function mapProperty(p) {
                                var name = displayName(p),
                                    duplicates = self.groupedByDisplay[name].length > 1;

                                self.queryPropertyMap[p.title] = p;

                                return JSON.stringify({
                                    displayName: name,
                                    title: p.title,
                                    propertyGroup: p.propertyGroup,
                                    duplicates: duplicates
                                });
                            }

                            function sortByPropertyGroupAndDisplayName(itemJson) {
                                var item = JSON.parse(itemJson),
                                    lower = item.displayName.toLowerCase();

                                if (item.propertyGroup) {
                                    return '1' + item.propertyGroup + lower;
                                }
                                return '0' + lower;
                            }
                        },
                        matcher: function(itemJson) {
                            if (this.query === ' ') return -1;

                            var item = JSON.parse(itemJson);

                            if (
                                this.query &&
                                self.currentProperty &&
                                self.currentProperty.title === item.title) {
                                return 1;
                            }
                            return Object.getPrototypeOf(this).matcher.apply(this, [item.displayName]);
                        },
                        highlighter: function(itemJson) {
                            var item = JSON.parse(itemJson);
                            if (item.duplicates) {
                                return item.displayName +
                                    _.template('<div title="{title}" class="subtitle">{title}</div>')(item)
                            }
                            return item.displayName;
                        },
                        sorter: function(items) {
                            var query = this.query;

                            return _.sortBy(items, function(json) {
                                var item = JSON.parse(json),
                                    displayName = item.displayName,
                                    group = item.propertyGroup;

                                if (query) {
                                    if (displayName === query) {
                                        return '0';
                                    }

                                    return '1' + displayName;
                                } else {
                                    if (group) {
                                        return '1' + displayName;
                                    }
                                    return '0' + displayName;
                                }
                            });
                        },
                        updater: function(itemJson) {
                            var item = JSON.parse(itemJson);
                            self.propertySelected(item);
                            return item;
                        }
                    }),
                    typeahead = field.data('typeahead');

                if (this.attr.focus) {
                    this.select('findPropertySelection').focus();
                }

                self.allowEmptyLookup(field);
                typeahead.render = function(items) {
                    var self = this,
                        $items = $(),
                        lastGroup;

                    items.forEach(function(item, i) {
                        var itemJson = JSON.parse(item);
                        if (itemJson.propertyGroup && lastGroup !== itemJson.propertyGroup) {
                            lastGroup = itemJson.propertyGroup;
                            $items = $items.add($('<li class="divider">'));
                            $items = $items.add($('<li class="nav-header">').text(itemJson.propertyGroup)[0]);
                        }

                        var $item = $(self.options.item).attr('data-value', item)
                            .toggleClass('active', i === 0)
                            .find('a').html(self.highlighter(item))
                            .end();
                        $items = $items.add($item);
                    })

                    this.$menu.empty().append($items)
                    return this;
                };
                typeahead.next = function(event) {
                    var active = this.$menu.find('.active').removeClass('active'),
                        next = active.nextAll(':not(.nav-header,.divider)').first();

                    if (!next.length) {
                        next = $(this.$menu.find('li:not(.nav-header,.divider)')[0])
                    }

                    next.addClass('active')
                };
                typeahead.prev = function(event) {
                    var active = this.$menu.find('.active').removeClass('active'),
                        prev = active.prevAll(':not(.nav-header,.divider)').first();

                    if (!prev.length) {
                        prev = this.$menu.find('li:not(.nav-header,.divider)').last()
                    }

                    prev.addClass('active')
                };
            }

            this.updatePropertiesSource();
        });

        this.onFilterProperties = function(event, data) {
            this.updatePropertiesSource(data.properties);
        };

        this.propertySelected = function(item) {
            var property = this.queryPropertyMap[item.title];

            if (property) {
                this.currentProperty = property;
                this.trigger('propertyselected', { property: property });
                _.defer(function() {
                    this.select('findPropertySelection').blur();
                }.bind(this));
            }
        };

        this.updatePropertiesSource = function(filtered) {
            var properties = filtered || this.attr.properties;

            this.groupedByDisplay = _.groupBy(properties, displayName);
            this.propertiesForSource = properties;
            this.dependentPropertyIris = _.chain(properties)
                .pluck('dependentPropertyIris')
                .compact()
                .flatten()
                .value();

            var hasProperties = this.filterSourceProperties(this.propertiesForSource).length > 0;
            var placeholderMessage = hasProperties ? 'field.selection.placeholder' : 'field.selection.no_valid';

            this.select('findPropertySelection')
                .attr('placeholder', i18n(placeholderMessage))
                .attr('disabled', !hasProperties);
        };

        this.filterSourceProperties = function(properties) {
            var self = this;

            return properties.filter(function(p) {
                    var visible = p.userVisible !== false;

                    if (self.attr.showAdminProperties) {
                        return true;
                    }

                    if (self.attr.unsupportedProperties &&
                        ~self.attr.unsupportedProperties.indexOf(p.title)) {
                        return false;
                    }

                    if (~HIDE_PROPERTIES.indexOf(p.title)) {
                        return false;
                    }

                    if (self.attr.onlySearchable !== true &&
                        self.attr.rollupCompound !== false &&
                        ~self.dependentPropertyIris.indexOf(p.title)) {
                        return false;
                    }

                    if (self.attr.rollupCompound === false &&
                       p.dependentPropertyIris) {
                        return false;
                    }

                    if (self.attr.onlySearchable) {
                        if (p.title === 'http://visallo.org#text') {
                            return true;
                        }
                        return visible && p.searchable !== false;
                    }

                    return visible;
                });
        };
    }

    function displayName(p) {
        return p.displayName || p.title;
    }
});
