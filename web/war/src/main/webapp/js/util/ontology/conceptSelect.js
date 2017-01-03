define([
    'flight/lib/component',
    './concepts.hbs',
    './concept.hbs',
    'util/withDataRequest',
    'util/requirejs/promise!util/service/ontologyPromise',
    './withSelect'
], function(
    defineComponent,
    template,
    conceptTemplate,
    withDataRequest,
    ontology,
    withSelect) {
    'use strict';

    return defineComponent(ConceptSelector, withDataRequest, withSelect);

    function ConceptSelector() {

        this.defaultAttrs({
            defaultText: i18n('concept.field.placeholder'),
            fieldSelector: 'input',
            showAdminConcepts: false,
            onlySearchable: false,
            restrictConcept: '',
            limitRelatedToConceptId: '',
            maxItems: withSelect.maxItemsFromConfiguration('typeahead.concepts.maxItems')
        });

        this.after('initialize', function() {
            this.$node.html(template(this.attr));

            this.on('click', {
                fieldSelector: this.showTypeahead
            });

            this.on('clearSelectedConcept', this.onClearConcept);
            this.on('selectConceptId', this.onSelectConceptId);
            this.on('enableConcept', this.onEnableConcept);

            this.setupTypeahead();
        });

        this.onSelectConceptId = function(event, data) {
            var self = this;
            Promise.resolve(
                this.conceptsById ||
                Promise.resolve(ontology.concepts).then(this.transformConcepts.bind(this))
            )
                .then(function() {
                    var concept = data && data.conceptId && self.conceptsById[data.conceptId];
                    self.select('fieldSelector').val(concept && concept.displayName || '');
                })
                .done();
        };

        this.showTypeahead = function() {
            this.select('fieldSelector').typeahead('lookup').select();
        }

        this.onConceptSelected = function(event) {
            var index = event.target.selectedIndex;

            this.trigger('conceptSelected', {
                concept: index > 0 ? this.allConcepts[index - 1].rawConcept : null
            });
        };

        this.onClearConcept = function(event) {
            this.select('fieldSelector').val('');
        };

        this.onEnableConcept = function(event, data) {
            if (data.disable || !data.enable) {
                this.select('conceptSelector').attr('disabled', true);
            } else {
                this.select('conceptSelector').removeAttr('disabled');
            }
        };

        this.setupTypeahead = function() {
            var self = this;

            Promise.resolve(ontology.concepts)
                .then(this.transformConcepts.bind(this))
                .done(function(concepts) {
                    concepts.splice(0, 0, self.attr.defaultText);

                    var field = self.select('fieldSelector')
                        .attr('placeholder', self.attr.defaultText),
                        selectedConcept;

                    if (self.attr.selectedConceptId) {
                        selectedConcept = self.conceptsById[self.attr.selectedConceptId];
                    }

                    if (self.attr.selectedConceptIntent) {
                        selectedConcept = _.find(self.allConcepts, function(c) {
                            if (c.rawConcept && c.rawConcept.intents) {
                                return _.contains(
                                    c.rawConcept.intents,
                                    self.attr.selectedConceptIntent
                                );
                            }
                        });
                    }

                    if (selectedConcept) {
                        field.val(selectedConcept.displayName);
                        _.defer(function() {
                            self.trigger('conceptSelected', { concept: selectedConcept });
                        })
                    }

                    field.typeahead({
                        minLength: 0,
                        items: self.attr.maxItems,
                        source: concepts,
                        matcher: function(concept) {
                            if ($.trim(this.query) === '') {
                                return true;
                            }
                            if (concept === self.attr.defaultText) {
                                return false;
                            }

                            return Object.getPrototypeOf(this).matcher.call(this, concept.flattenedDisplayName);
                        },
                        sorter: _.identity,
                        updater: function(conceptId) {
                            var $element = this.$element,
                                concept = self.conceptsById[conceptId];

                            self.trigger('conceptSelected', { concept: concept && concept.rawConcept });
                            return concept && concept.displayName || '';
                        },
                        highlighter: function(concept) {
                            return conceptTemplate(concept === self.attr.defaultText ?
                            {
                                concept: {
                                    displayName: concept,
                                    rawConcept: { }
                                },
                                path: null,
                                marginLeft: 0
                            } : {
                                concept: concept,
                                path: concept.flattenedDisplayName.replace(/\/?[^\/]+$/, ''),
                                marginLeft: concept.depth
                            });
                        }
                    })

                    if (self.attr.focus) {
                        window.focus();
                        _.defer(function() {
                            field.focus().select();
                        })
                    }

                    self.allowEmptyLookup(field);
                });
        }

        this.transformConcepts = function(concepts) {
            var self = this,
                limitRelatedSearch;

            if (this.attr.limitRelatedToConceptId) {
                limitRelatedSearch = this.dataRequest('ontology', 'relationships');
            } else {
                limitRelatedSearch = Promise.resolve();
            }

            return new Promise(function(fulfill, reject) {
                limitRelatedSearch.done(function(r) {
                    self.allConcepts = _.chain(
                            concepts[self.attr.showAdminConcepts ? 'forAdmin' : 'byTitle']
                        )
                        .filter(function(c) {
                            if (c.userVisible === false && self.attr.showAdminConcepts !== true) {
                                return false;
                            }

                            if (self.attr.restrictConcept) {

                                // Walk up tree to see if any match
                                var parentConceptId = c.id,
                                    shouldRestrictConcept = true;
                                do {
                                    if (self.attr.restrictConcept === parentConceptId) {
                                        shouldRestrictConcept = false;
                                        break;
                                    }
                                } while (
                                    parentConceptId &&
                                    (parentConceptId = concepts.byId[parentConceptId].parentConcept)
                                );

                                if (shouldRestrictConcept) {
                                    return false;
                                }
                            }

                            if (self.attr.onlySearchable && c.searchable === false) {
                                return false;
                            }

                            if (self.attr.limitRelatedToConceptId &&
                               r && r.groupedByRelatedConcept &&
                               r.groupedByRelatedConcept[self.attr.limitRelatedToConceptId]) {
                                if (r.groupedByRelatedConcept[self.attr.limitRelatedToConceptId].indexOf(c.id) === -1) {
                                    return false;
                                }
                            }

                            if (self.attr.limitRelatedToConceptId) {
                                var relatedToConcept = concepts.byId[self.attr.limitRelatedToConceptId];
                                if (relatedToConcept &&
                                    relatedToConcept.addRelatedConceptWhiteList &&
                                    relatedToConcept.addRelatedConceptWhiteList.indexOf(c.id) === -1) {
                                    return false;
                                }
                            }

                            return true;
                        })
                        .map(function(c) {
                            return {
                                id: c.id,
                                toString: function() {
                                    return this.id;
                                },
                                displayName: c.displayName,
                                flattenedDisplayName: c.flattenedDisplayName,
                                depth: c.flattenedDisplayName
                                         .replace(/[^\/]/g, '').length,
                                selected: self.attr.selected === c.id,
                                rawConcept: c
                            }
                        })
                        .value();

                    self.conceptsById = _.indexBy(self.allConcepts, 'id');

                    fulfill(self.allConcepts);
                });
            });
        }
    }
});
