(function () {

    'use strict';

    /**
    * Checks if the given entity field has a metadata with the given key and value.
    *
    * @param {Object} instance of entity field
    * @param {string} metadata key related with the value
    * @param {string} metadata value related with the key
    * @return {boolean} true if field has a metadata with the given key and value; otherwise
    *                   false
    */
    function hasMetadata(field, key, value) {
        var found, i;

        if (field.metadata) {
            for (i = 0; i < field.metadata.length; i += 1) {
                if (_.isEqual(field.metadata[i].key, key)) {
                    found = field.metadata[i];
                }
            }
        }

        return found && _.isEqual(found.value, value);
    }

    var controllers = angular.module('mds.controllers', []),
        workInProgress = {
            list: [],
            actualEntity: undefined,
            setList: function (service) {
                this.list = service.getWorkInProggress();
            },
            setActualEntity: function (service, entityId) {
                this.setList(service);
                this.actualEntity = entityId;
            }
        },
        loadEntity;

    controllers.controller('MdsBasicCtrl', function ($scope, $location, $route, Entities) {
        var schemaEditorPath = '/mds/{0}'.format($scope.AVAILABLE_TABS[0]);

        workInProgress.setList(Entities);

        $scope.hasWorkInProgress = function () {
            var expression = workInProgress.list.length > 0,
                idx;

            for (idx = 0; expression && idx < workInProgress.list.length; idx += 1) {
                if (workInProgress.list[idx].id === workInProgress.actualEntity) {
                    expression = false;
                }
            }

            return expression;
        };

        $scope.getWorkInProgress = function () {
            var list = [];

            angular.forEach(workInProgress.list, function (entity) {
                if (entity.id !== workInProgress.actualEntity) {
                    list.push(entity);
                }
            });

            return list;
        };

        $scope.resumeEdits = function (entityId) {
            if (schemaEditorPath !== $location.path()) {
                $location.path(schemaEditorPath);
            } else {
                $route.reload();
            }

            loadEntity = entityId;
        };

        $scope.discard = function (entityId) {
            motechConfirm('mds.wip.info.discard', 'mds.warning', function (val) {
                if (val) {
                    Entities.abandon({id: entityId}, function () {
                        workInProgress.setList(Entities);
                    });
                }
            });
        };

        $scope.closePeriodModal = function () {
            $('body').children("#periodModal").modal('hide');
        };

    });

    /**
    * The SchemaEditorCtrl controller is used on the 'Schema Editor' view.
    */
    controllers.controller('SchemaEditorCtrl', function ($scope, $timeout, Entities, Users, Roles, MDSUtils, Locale) {
        var setAdvancedSettings, setRest, setBrowsing, setSecuritySettings, setIndexesLookupsTab;

        innerLayout({
            spacing_closed: 30,
            east__minSize: 200,
            east__maxSize: 350
        });

        workInProgress.setList(Entities);

        if (loadEntity) {
            $.ajax("../mds/entities/" + loadEntity).done(function (data) {
                $scope.selectedEntity = data;
                loadEntity = undefined;
            });
        }

        /**
        * This function is used to get entity advanced rest data from controller and prepare it for further usage.
        */
        setRest = function () {
            $scope.selectedEntityAdvancedFields = [];
            $scope.selectedEntityAdvancedAvailableFields = [];
            $scope.selectedEntityRestLookups = [];

            if ($scope.advancedSettings.restOptions) {
                angular.forEach($scope.advancedSettings.restOptions.fieldIds, function (id) {
                    $scope.selectedEntityAdvancedFields.push($scope.findFieldById(id));
                });
            }

            angular.forEach($scope.fields, function (field) {
                if (!$scope.findFieldInArrayById(field.id, $scope.selectedEntityAdvancedFields)) {
                    $scope.selectedEntityAdvancedAvailableFields.push($scope.findFieldById(field.id));
                }
            });

            if ($scope.advancedSettings.indexes) {
                angular.forEach($scope.advancedSettings.indexes, function (lookup, index) {
                    if ($.inArray(lookup.id, $scope.advancedSettings.restOptions.lookupIds) !== -1) {
                        $scope.selectedEntityRestLookups[index] = true;
                    } else {
                        $scope.selectedEntityRestLookups[index] = false;
                    }
                });
            }
        };

        /**
        * This function splits fields to ones that are displayed and ones that are not
        */
        setBrowsing = function() {
            if($scope.fields !== undefined && $scope.advancedSettings.browsing !== undefined) {
                $scope.browsingDisplayed = [];

                $scope.browsingAvailable = $.grep($scope.fields, function(field) {
                    return $scope.advancedSettings.browsing.displayedFields.indexOf(field.id) < 0;
                });

                $scope.browsingDisplayed.length = 0;
                angular.forEach($scope.advancedSettings.browsing.displayedFields, function(fieldId) {
                    $scope.browsingDisplayed.push($.grep($scope.fields, function(field) {
                        return field.id === fieldId;
                    })[0]);
                });

                $scope.browsingAvailable.sort(function(a,b) {
                    if (a.basic.displayName < b.basic.displayName) { return -1; }
                    if (a.basic.displayName > b.basic.displayName) { return 1; }
                    return 0;
                });
            }
        };

        /**
        * This function defines default behaviour on indexesLookupsTab shown event
        */
        setIndexesLookupsTab = function() {
            $('#indexesLookupsTabLink').on('shown.bs.tab', function (e) {
                $scope.setLookupFocus();
            });

            $('#advancedObjectSettingsModal').on('shown.bs.modal', function () {
                $scope.setLookupFocus();
            });
        };

        /**
        * This function is used to set advanced settings. If settings is properly taken from server,
        * the related $scope fields will be also set.
        */
        setAdvancedSettings = function () {
            $scope.advancedSettings = Entities.getAdvanced({id: $scope.selectedEntity.id},
                function () {
                    $scope.blockLookups = false;
                    if (!_.isNull($scope.advancedSettings)
                            && !_.isUndefined($scope.advancedSettings)
                            && $scope.advancedSettings.indexes.length > 0) {
                        $scope.setActiveIndex(0);
                    } else {
                        $scope.setActiveIndex(-1);
                    }

                    setRest();
                    setBrowsing();
                    setIndexesLookupsTab();

                    angular.forEach($scope.advancedSettings.indexes, function(index) {
                        var result = $.grep($scope.advancedSettings.indexes, function(lookup) {
                            return lookup.lookupName === index.lookupName;
                        });

                        if (result.length > 1) {
                            $scope.setActiveIndex($scope.advancedSettings.indexes.indexOf(index));
                            $scope.blockLookups = true;
                            return;
                        }
                    });
                    unblockUI();
                });
        };

        /**
        * This function is used to set security settings by getting them from the server.
        */
        setSecuritySettings = function () {
            $scope.securitySettings = {};
            $scope.securitySettings.roles = [];
            $scope.securitySettings.users = [];

            if ($scope.selectedEntity === null) {
                return;
            }

            $scope.securitySettings.securityMode =  $scope.selectedEntity.securityMode.valueOf();

            if ($scope.securitySettings.securityMode === 'USERS'){
                $scope.securitySettings.users = $scope.selectedEntity.securityMembers;
            } else if ($scope.securitySettings.securityMode === 'ROLES'){
                $scope.securitySettings.roles = $scope.selectedEntity.securityMembers;
            }

            $('#usersSelect').select2('val', $scope.securitySettings.users);
            $('#rolesSelect').select2('val', $scope.securitySettings.roles);
        };

        /**
        * The $scope.selectedEntityAdvancedAvailableFields contains fields available for use in REST.
        */
        $scope.selectedEntityAdvancedAvailableFields = [];

        /**
        * The $scope.selectedEntityAdvancedFields contains fields selected for use in REST.
        */
        $scope.selectedEntityAdvancedFields = [];

        /**
        * The $scope.selectedEntityRestLookups contains lookups selected for use in REST.
        */
        $scope.selectedEntityRestLookups = [];

        /**
        * The $scope.selectedEntityMetadata contains orignal metadata for selected entity used to check
        * for changes in it.
        */
        $scope.originalSelectedEntityMetadata = undefined;

        /**
        * The $scope.selectedEntity contains selected entity. By default no entity is selected.
        */
        $scope.selectedEntity = null;

        /**
        * The $scope.advancedSettings contains advanced settings of selected entity. By default
        * there are no advanced settings
        */
        $scope.advancedSettings = null;

        /**
        * The $scope.securitySettings contains security settings of selected entity. By default
        * there are no security settings
        */
        $scope.securitySettings = {};
        $scope.securitySettings.roles = [];
        $scope.securitySettings.users = [];
        $scope.securitySettings.securityMode = undefined;

        /**
        * The $scope.fields contains entity fields. By default there are no fields.
        */
        $scope.fields = undefined;

        /**
        * The $scope.newField contains information about new field which will be added to an
        * entity schema. By default no field is created.
        */
        $scope.newField = {};

        /**
        * The $scope.tryToCreate is used to ensure that error messages in the new field form will
        * be not visible until a user try to add a new field
        */
        $scope.tryToCreate = false;

        /**
        * The $scope.availableFields array contains information about fields, that can be selected
        * as lookup fields for certain index
        */
        $scope.availableFields = [];

        $scope.activeIndex = -1;

        /**
        * The $scope.lookup persists currently active (selected) index
        */
        $scope.lookup = undefined;

        /**
        * The $scope.browsingAvailable and $scope.browsingDisplayed separates fields that are
        * visible from the ones that are not.
        */
        $scope.browsingAvailable = [];
        $scope.browsingDisplayed = [];

        /**
        * The $scope.filterableTypes contains types that can be used as filters.
        */
        $scope.filterableTypes = [
            "mds.field.combobox", "mds.field.boolean", "mds.field.date",
            "mds.field.time", "mds.field.datetime"
        ];

        $scope.availableUsers = Users.query();
        $scope.availableRoles = Roles.query();
        $scope.availableLocale = Locale.get();

        $scope.currentError = undefined;

        $scope.setError = function(error, params) {
            var errorCode;

            if (error) {
                if (error.data) {
                    errorCode = error.data;

                    if (errorCode.startsWith('key:')) {
                        errorCode = errorCode.split(':')[1];
                    }
                } else if ($.type(error) === 'string') {
                    errorCode = error;
                }
            }

            $scope.currentError = $scope.msg(errorCode, params);
        };

        $scope.unsetError = function() {
            $scope.currentError = undefined;
        };

        $scope.draft = function (data, callback) {
            var pre = { id: $scope.selectedEntity.id },
            func = function (data) {
                $scope.unsetError();

                $scope.selectedEntity.modified = data.changesMade;
                $scope.selectedEntity.outdated = data.outdated;

                if (_.isFunction(callback)) {
                    callback();
                }
            },
            errorHandler = function(title, msg, params) {
                $scope.setError(msg, params);
            };

            Entities.draft(pre, data, func, angularHandler('mds.error', 'mds.error.draftSave', errorHandler));
        };

        /**
        * The $scope.SELECT_ENTITY_CONFIG contains configuration for selecting entity tag on UI.
        */
        $scope.SELECT_ENTITY_CONFIG = {
            ajax: {
                url: '../mds/selectEntities',
                dataType: 'json',
                quietMillis: 100,
                data: function (term, page) {
                    return {
                        term: term,
                        pageLimit: 5,
                        page: page
                    };
                },
                results: function (data) {
                    return data;
                }
            },
            initSelection: function (element, callback) {
                var id = $(element).val();

                if (!isBlank(id)) {
                    $.ajax("../mds/entities/" + id).done(function (data) {
                        callback(data);
                    });
                }
            },
            formatSelection: function (entity) {
                var name = entity && entity.name ? entity.name : '',
                    module = entity && entity.module ? ' {0}: {1}'
                        .format($scope.msg('mds.module'), entity.module) : '',
                    namespace = entity && entity.namespace ? ' {0}: {1}'
                        .format($scope.msg('mds.namespace'), entity.namespace) : '',
                    info = $.trim('{0} {1}'.format(module, namespace)),
                    label = !isBlank(info) && !isBlank(name)
                        ? '{0} ({1})'.format(name, info) : !isBlank(name) ? name : '';

                return isBlank(label) ? $scope.msg('mds.error') : label;
            },
            formatResult: function (entity) {
                var strong = entity && entity.name
                        ? angular.element('<strong>').text(entity.name)
                        : undefined,
                    name = strong
                        ? angular.element('<div>').append(strong)
                        : undefined,
                    module = entity && entity.module
                        ? angular.element('<span>')
                            .text(' {0}: {1}'.format($scope.msg('mds.module'), entity.module))
                        : undefined,
                    namespace = entity && entity.namespace
                        ? angular.element('<span>')
                            .text(' {0}: {1}'.format(
                                $scope.msg('mds.namespace'),
                                entity.namespace
                            ))
                        : undefined,
                    info1 = module
                        ? angular.element('<div>').append(module)
                        : undefined,
                    info2 = namespace
                        ? angular.element('<div>').append(namespace)
                        : undefined,
                    parent = (name || info1 || info2)
                        ? angular.element('<div>').append(name).append(info1).append(info2)
                        : undefined;

                return parent || $scope.msg('mds.error');
            },
            containerCssClass: "form-control-select2",
            escapeMarkup: function (markup) {
                return markup;
            }
        };

        /**
        * The $scope.SELECT_FIELD_TYPE_CONFIG contains configuration for selecting field type tag
        * on UI.
        */
        $scope.SELECT_FIELD_TYPE_CONFIG = {
            ajax: {
                url: '../mds/available/types',
                dataType: 'json',
                quietMillis: 100,
                data: function (term, page) {
                    return {
                        term: term,
                        pageLimit: 5,
                        page: page
                    };
                },
                results: function (data) {
                    return data;
                }
            },
            initSelection: function (element, callback) {
                var id = $(element).val();

                if (!isBlank(id)) {
                    $.ajax('../mds/available/types').done(function (data) {
                        var found, i;

                        for (i = 0; i < data.results.length; i += 1) {
                            if (data.results[i].id === id) {
                                found = data.results[i];
                                break;
                            }
                        }

                        callback(found);
                    });
                }
            },
            formatSelection: function (type) {
                return $scope.msg((type && type.displayName) || 'mds.error');
            },
            formatResult: function (type) {
                var strong = type && type.displayName
                        ? angular.element('<strong>').text($scope.msg(type.displayName))
                        : undefined,
                    name = strong
                        ? angular.element('<div>').append(strong)
                        : undefined,
                    description = type && type.description
                        ? angular.element('<span>')
                            .text($scope.msg(type.description))
                        : undefined,
                    info = description
                        ? angular.element('<div>').append(description)
                        : undefined,
                    parent = (name || info)
                        ? angular.element('<div>').append(name).append(info)
                        : undefined;

                return parent || $scope.msg('mds.error');
            },
            containerCssClass: "form-control-select2",
            escapeMarkup: function (markup) {
                return markup;
            }
        };

        /* ~~~~~ ENTITY FUNCTIONS ~~~~~ */

        /**
        * Create and save a new entity with a name from related input tag. If the value of input
        * tag is blank, error message will be shown and the entity will be not created.
        */
        $scope.createEntity = function () {
            var form = angular.element("#newEntityModalForm"),
                input = form.find('#inputEntityName'),
                help = input.next('span'),
                value = input.val(),
                entity = {};

            if (isBlank(value)) {
                help.removeClass('hide');
            } else {
                entity.name = value;
                blockUI();

                Entities.save({}, entity, function (response) {
                    $scope.selectedEntity = response;
                    angular.element('#selectEntity').select2('val', response.id);

                    $scope.clearEntityModal();
                    unblockUI();
                }, function (response) {
                    handleResponse('mds.error', 'mds.error.cantSaveEntity', response);
                    unblockUI();
                });
            }
        };

        /**
        * Remove value from the name input tag and hide the error message. This method also hides
        * the new entity modal window.
        */
        $scope.clearEntityModal = function () {
            var modal = angular.element('#newEntityModal'),
                form = modal.find('form'),
                spans = form.find('span.help-block');

            angular.forEach(spans, function (span) {
                var that = angular.element(span);

                if (!that.hasClass('hide')) {
                    that.addClass('hide');
                }
            });

            form.resetForm();
            modal.modal('hide');
        };

        /**
        * Deletes the selected entity. If the entity is read only (provided by module), action is
        * not allowed. If entity does not exist, error message is shown.
        */
        $scope.deleteEntity = function () {
            if ($scope.selectedEntity !== null) {
                Entities.remove({id: $scope.selectedEntity.id}, function () {
                    $scope.selectedEntity = null;
                    handleResponse('mds.success', 'mds.delete.success', '');
                }, function (response) {
                    handleResponse('mds.error', 'mds.error.cantDeleteEntity', response);
                });
            }
        };

        /* ~~~~~ METADATA FUNCTIONS ~~~~~ */

        /**
        * Adds new metadata with empty key/value to field.
        */
        $scope.addMetadata = function (field) {
            $scope.draft({
                edit: true,
                values: {
                    path: '$addEmptyMetadata',
                    fieldId: field.id
                }
            }, function () {
                $scope.safeApply(function () {
                    if (!field.metadata) {
                        field.metadata = [];
                    }

                    field.metadata.push({key: '', value: ''});
                });
            });
        };

        /**
        * Removes selected metadata entry from field.
        */
        $scope.removeMetadata = function (field, idx) {
            $scope.draft({
                edit: true,
                values: {
                    path: '$removeMetadata',
                    fieldId: field.id,
                    value: [idx]
                }
            }, function () {
                $scope.safeApply(function () {
                    field.metadata.remove(idx);
                });
            });
        };

        $scope.draftRestLookup = function (index) {
            var value = $scope.selectedEntityRestLookups[index],
                lookup = $scope.advancedSettings.indexes[index];

            $scope.draft({
                edit: true,
                values: {
                    path: 'restOptions.${0}'.format(value ? 'addLookup' : 'removeLookup'),
                    advanced: true,
                    value: [lookup.id]
                }
            }, function () {
                $scope.safeApply(function () {
                    if (value) {
                        $scope.advancedSettings.restOptions.lookupIds.push(
                            lookup.id
                        );
                    } else {
                        $scope.advancedSettings.restOptions.lookupIds.removeObject(
                            lookup.id
                        );
                    }
                });
            });
        };

        /**
        * Callback function called each time when user adds, removes or moves items in 'Displayed Fields' on
        * 'REST API' view. Responsible for updating the model.
        */
        $scope.onRESTDisplayedChange = function(container) {
            $scope.advancedSettings.restOptions.fieldIds = [];

            angular.forEach(container, function(field) {
                $scope.advancedSettings.restOptions.fieldIds.push(field.id);
            });

            $scope.draft({
                edit: true,
                values: {
                    path: 'restOptions.$setFieldIds',
                    advanced: true,
                    value: [$scope.advancedSettings.restOptions.fieldIds]
                }
            });
        };

        /* ~~~~~ FIELD FUNCTIONS ~~~~~ */

        /**
        * Create new field and add it to an entity schema. If displayName, name or type was not
        * set, error message will be shown and a field will not be created. The additional message
        * will be shown if a field name is not unique.
        */
        $scope.createField = function () {
            var validate, selector;

            $scope.tryToCreate = true;
            validate = $scope.newField.type
                && $scope.newField.displayName
                && $scope.newField.name
                && $scope.findFieldsByName($scope.newField.name).length === 0;

            if (validate) {
                $scope.draft({
                    create: true,
                    values: {
                        typeClass: $scope.newField.type.typeClass,
                        displayName: $scope.newField.displayName,
                        name: $scope.newField.name
                    }
                }, function () {
                    var field;

                    field = Entities.getField({
                        id: $scope.selectedEntity.id,
                        param: $scope.newField.name
                    }, function () {
                        $scope.fields.push(field);
                        $scope.advancedSettings.browsing.displayedFields.push(field.id);
                        setBrowsing();
                        setRest();

                        selector = '#show-field-details-{0}'.format($scope.fields.length - 1);
                        $scope.newField = {};
                        angular.element('#newField').select2('val', null);
                        $scope.tryToCreate = false;

                        angular.element(selector).livequery(function () {
                            var elem = angular.element(selector);

                            elem.click();
                            elem.expire();
                        });
                    });
                });
            }
        };

        /**
        * Remove a field from an entity schema. The selected field will be removed only if user
        * confirms that the user wants to remove the field from the entity schema.
        *
        * @param {object} field The field which should be removed.
        */
        $scope.removeField = function (field) {
            motechConfirm('mds.warning.removeField', 'mds.warning', function (val) {
                if (val) {
                    $scope.draft({
                        remove: true,
                        values: {
                            fieldId: field.id
                        }
                    }, function() {
                         $scope.safeApply(function () {
                            var filterableIndex;
                            $scope.fields.removeObject(field);

                            if ($scope.findFieldInArrayById(field.id, $scope.selectedEntityAdvancedAvailableFields)) {
                                $scope.selectedEntityAdvancedAvailableFields.removeObject(field);
                            } else {
                                $scope.selectedEntityAdvancedFields.removeObject(field);
                            }

                            filterableIndex = $scope.advancedSettings.browsing.filterableFields.indexOf(field.id);
                            if(filterableIndex >= 0) {
                                $scope.advancedSettings.browsing.filterableFields.splice(filterableIndex, 1);
                            }

                            setBrowsing();
                            setRest();
                        });
                    });
                }
            });
        };

        $scope.shouldShowField = function(field) {
            return !hasMetadata(field, 'autoGenerated', 'true') && !hasMetadata(field, 'autoGeneratedEditable', 'true');
        };

        /**
        * Abandon all changes made on an entity schema.
        */
        $scope.abandonChanges = function () {
            $scope.unsetError();

            blockUI();

            $scope.selectedEntity.outdated = false;

            Entities.abandon({id: $scope.selectedEntity.id}, function () {
                var entity;

                entity = Entities.get({id: $scope.selectedEntity.id },  function () {
                    $scope.selectedEntity = entity;
                });
            });
        };

        /**
        * Update the draft.
        */
        $scope.updateDraft = function () {
            var entity;

            blockUI();

            $scope.unsetError();
            $scope.selectedEntity.outdated = false;

            entity = Entities.update({id: $scope.selectedEntity.id}, function () {
                $scope.selectedEntity = entity;
            });
        };

        /**
        * Check if the given field is unique.
        *
        * @param {string} fieldName The field name to check.
        * @return {boolean} true if the given field is unique; otherwise false.
        */
        $scope.uniqueField = function (fieldName) {
            return $scope.findFieldsByName(fieldName).length === 1;
        };

        /**
        * Check if the given metadata key is unique.
        *
        * @param {string} key metadata key to check..
        * @return {boolean} true if the given key is unique; otherwise false.
        */
        $scope.uniqueMetadataKey = function (field, key) {
            return !_.isUndefined(key)
                && MDSUtils.find(field.metadata, [{ field: 'key', value: key}], false).length === 1;
        };

        /**
        * Validate all information inside the given field.
        *
        * @param {object} field The field to validate.
        * @return {boolean} true if all information inside the field are correct; otherwise false.
        */
        $scope.validateField = function (field) {
            return $scope.validateFieldBasic(field)
                && $scope.validateFieldSettings(field)
                && $scope.validateFieldValidation(field);
        };

        /**
        * Validate the basic information ('Basic' tab on UI) inside the given field.
        *
        * @param {object} field The field to validate.
        * @return {boolean} true if all basic information inside the field are correct;
        *                   otherwise false.
        */
        $scope.validateFieldBasic = function (field) {
            return field.basic.displayName
                && field.basic.name
                && $scope.uniqueField(field.basic.name);
        };

        /**
        * Validate the settings information ('Settings' tab on UI) inside the given field.
        *
        * @param {object} field The field to validate.
        * @return {boolean} true if all settings information inside the field are correct;
        *                   otherwise false.
        */
        $scope.validateFieldSettings = function (field) {
            var expression = true;

            if (field.settings) {
                angular.forEach(field.settings, function (setting) {
                    expression = expression && $scope.checkOptions(setting);
                });
            }

            return expression;
        };

        /**
        * Validate the validation information ('Validation' tab on UI) inside the given field.
        *
        * @param {object} field The field to validate.
        * @return {boolean} true if all validation information inside the field are correct;
        *                   otherwise false.
        */
        $scope.validateFieldValidation = function (field) {
            var expression = true;

            if (field.validation) {
                angular.forEach(field.validation.criteria, function (criterion) {
                    if ($scope.validateCriterion(criterion, field.validation.criteria)) {
                        expression = false;
                    }
                });
            }

            return expression;
        };

        /**
        * Check if a user can save field definitions to database.
        *
        * @return {boolean} true if field definitions are correct; otherwise false.
        */
        $scope.canSaveChanges = function () {
            var expression = true;

            angular.forEach($scope.fields, function (field) {
                expression = expression && $scope.validateField(field);

                angular.forEach(field.metadata, function (meta) {
                    expression = expression && $scope.uniqueMetadataKey(field, meta.key);
                });
            });

            if ($scope.advancedSettings && $scope.advancedSettings.indexes) {
                angular.forEach($scope.advancedSettings.indexes, function (index) {
                    expression = expression && index.lookupName !== undefined && index.lookupName.length !== 0 && !$scope.blockLookups;
                });
            }

            return expression;
        };

        /**
        * Save all changes made on an entity schema. Firstly this method tries to save fields to
        * database one by one. Next the method tries to delete existing fields from database.
        */
        $scope.saveChanges = function () {
            var pre = { id: $scope.selectedEntity.id },
                data = {},
                successCallback = function (data) {
                    var pre = {id: $scope.selectedEntity.id},
                        successCallback = function () {
                            setAdvancedSettings();
                            unblockUI();
                        };

                    $scope.selectedEntity.modified = false;
                    $scope.selectedEntity.outdated = false;
                    $scope.fields = Entities.getFields(pre, successCallback);
                },
                errorCallback = function (data) {
                    $scope.setError(data);
                    unblockUI();
                };

            blockUI();
            Entities.commit(pre, data, successCallback, errorCallback);
        };

        /* ~~~~~ ADVANCED FUNCTIONS ~~~~~ */

        /**
        * Add what field should be logged. If the field exists in the array, the field will be
        * removed from the array.
        *
        * @param {object} field The object which represent the entity field.
        */
        $scope.addFieldToLog = function (field) {
            var idx;

            if (!_.isNull($scope.advancedSettings) && !_.isUndefined($scope.advancedSettings)) {
                idx = $scope.advancedSettings.tracking.fields.indexOf(field.id);

                $scope.draft({
                    edit: true,
                    values: {
                        path: idx > -1 ? 'tracking.$removeField' : 'tracking.$addField',
                        advanced: true,
                        value: [field.id]
                    }
                }, function () {
                    if (idx > -1) {
                        $scope.advancedSettings.tracking.fields.remove(idx);
                    } else {
                        $scope.advancedSettings.tracking.fields.push(field.id);
                    }
                });
            }
        };

        /**
        * Add what kind of action should be logged. If the action exists in the array, the action
        * will be removed from the array.
        *
        * @param {string} action The name of the action to log.
        */
        $scope.addActionToLog = function (action) {
            var idx;

            if (!_.isNull($scope.advancedSettings) && !_.isUndefined($scope.advancedSettings)) {
                idx = $scope.advancedSettings.tracking.actions.indexOf(action);

                $scope.draft({
                    edit: true,
                    values: {
                        path: idx > -1 ? 'tracking.$removeAction' : 'tracking.$addAction',
                        advanced: true,
                        value: [action]
                    }
                }, function () {
                    if (idx > -1) {
                        $scope.advancedSettings.tracking.actions.remove(idx);
                    } else {
                        $scope.advancedSettings.tracking.actions.push(action);
                    }
                });
            }
        };

        $scope.checkLookupName = function (count) {
            return $.grep($scope.advancedSettings.indexes, function(lookup) {
                return (lookup.lookupName.toLowerCase() === "lookup " + count ||
                        lookup.lookupName.toLowerCase() === "lookup" + count);
            });
        };

        $scope.getLookupNameForNewLookup = function () {
            var count = 1, result;

            while (true) {
                result = $scope.checkLookupName(count);

                if (result.length === 0) {
                    return "Lookup " + count;
                }
                count += 1;
            }
        };

        /**
        * Adds a new index and sets it as the active one
        */
        $scope.addNewIndex = function () {
            var newLookup = {
                lookupName: $scope.getLookupNameForNewLookup(),
                singleObjectReturn: true,
                lookupFields: []
            };

            $scope.draft({
                edit: true,
                values: {
                    path: '$addNewIndex',
                    advanced: true,
                    value: [newLookup.lookupName]
                }
            }, function () {
                $scope.advancedSettings.indexes.push(newLookup);
                $scope.setActiveIndex($scope.advancedSettings.indexes.length-1);
            });
        };
        $scope.blockLookups = false;
        $scope.$watch('lookup.lookupName', function () {
            var exists;

            if ($scope.advancedSettings !== null && $scope.lookup.lookupName !== undefined) {
                blockUI();
                $scope.validateLookupName($scope.lookup.lookupName);
                unblockUI();
            }
        });

        /**
        * Runs a validation for given lookup name. If there's a duplicate in the current array of
        * lookups, it will perform necessary actions (display error and block components on UI). Otherwise
        * the error message and component block will be removed.
        *
        * @lookupName   Name of the lookup to perform check for.
        */
        $scope.validateLookupName = function(lookupName) {
            var exists;
            exists = MDSUtils.find($scope.advancedSettings.indexes, [{ field: 'lookupName', value: lookupName }], false, true).length > 1;

            if (exists) {
                $(".lookupExists").show();
                $scope.blockLookups = true;
            } else {
                $(".lookupExists").hide();
                $scope.blockLookups = false;
            }
        };

        /**
        * Specifies, whether a certain index is a currently active one
        *
        * @param index An index in array of index object to check
        * @return {boolean} True, if passed index is the active one. False otherwise.
        */
        $scope.isActiveIndex = function (index) {
            return $scope.activeIndex === index;
        };

        /**
        * Sets certain index as the currently active one
        *
        * @param index  An index in array of index object to set active
        */
        $scope.setActiveIndex = function (index) {
            if (!$scope.blockLookups) {
                $scope.activeIndex = index;
                if ($scope.activeIndex > -1) {
                    $scope.lookup = $scope.advancedSettings.indexes[$scope.activeIndex];
                    $scope.setAvailableFields();
                    $scope.setLookupFocus();
                } else {
                    $scope.lookup = undefined;
                }
            }
        };

        /**
        * Changes active index depending on which arrow key user pressed
        * up arrow - decrements active index
        * down arrow - increments active index
        */
        $scope.changeActiveIndex = function($event) {
            if (!$scope.blockLookups) {
                if ($event.keyCode === 38 && $scope.activeIndex > 0) { // up arrow
                    $scope.setActiveIndex($scope.activeIndex - 1);
                } else if ($event.keyCode === 40 && $scope.activeIndex < $scope.advancedSettings.indexes.length - 1) { // down arrow
                    $scope.setActiveIndex($scope.activeIndex + 1);
                }
            }
        };

        /**
        * Sets focus on lookup with active index
        */
        $scope.setLookupFocus = function() {
            var selector;
            if ($scope.activeIndex !== -1) {
                selector = '#lookup-{0}'.format($scope.activeIndex);
                $(selector).livequery(function () {
                    var elem = $(selector);
                    elem.focus();
                    elem.expire();
                });
            }
        };

        /**
        * Removes currently active index
        */
        $scope.deleteLookup = function () {
            var deletedLookupName;
            $scope.draft({
                edit: true,
                values: {
                    path: '$removeIndex',
                    advanced: true,
                    value: [$scope.activeIndex]
                }
            }, function () {
                deletedLookupName = $scope.advancedSettings.indexes[$scope.activeIndex].lookupName;
                $scope.advancedSettings.indexes.remove($scope.activeIndex);
                $scope.selectedEntityRestLookups.splice($scope.activeIndex, 1);
                $scope.setActiveIndex(-1);
                $scope.validateLookupName(deletedLookupName);
            });
        };

        /**
        * Adds new lookup field to the currently active index
        */
        $scope.addLookupField = function () {
            var value = $scope.availableFields[0] && $scope.availableFields[0].id;

            $scope.draft({
                edit: true,
                values: {
                    path: 'indexes.{0}.$addField'.format($scope.activeIndex),
                    advanced: true,
                    value: [value]
                }
            }, function () {
                $scope.advancedSettings.indexes[$scope.activeIndex].lookupFields.push({id: value});
                $scope.setAvailableFields();
            });
        };

        /**
        * Handles field selection. When clicking on one of the available fields from dropdown list,
        * selected field is added or replaced on the list of selected fields of the currently
        * active index
        *
        * @param oldField Previously selected field index
        * @param field Selected field index
        */
        $scope.selectField = function (oldField, newField) {
            var i, selectedIndex, lookupFields = $scope.advancedSettings.indexes[$scope.activeIndex].lookupFields;

            for (i = 0; i < lookupFields.length; i += 1) {
                if (lookupFields[i].id === oldField) {
                    selectedIndex = i;
                    break;
                }
            }

            $scope.draft({
                edit: true,
                values: {
                    path: 'indexes.{0}.$insertField'.format($scope.activeIndex),
                    advanced: true,
                    value: [selectedIndex, newField]
                }
            }, function () {
                lookupFields[selectedIndex] = { id: newField };
                $scope.setAvailableFields();
            });
        };

        /**
        * Refreshes available fields for the currently active index. A field is considered available
        * if it is not yet present in the lookup field list of the currently active index.
        */
        $scope.setAvailableFields = function () {
            var availableFields = [], func, selectedFields, i;

            if ($scope.activeIndex !== -1) {
                func = function (num) { return num.id === $scope.fields[i].id; };
                selectedFields = $scope.advancedSettings.indexes[$scope.activeIndex].lookupFields;

                for (i = 0; i < $scope.fields.length; i += 1) {
                    if (_.filter(selectedFields, func).length === 0) {
                        availableFields.push($scope.fields[i]);
                    }
                }

                $scope.availableFields = availableFields;
            }
        };

        /**
        * Removes given field from the lookup fields list of the currently active index
        *
        * @param field A field object to remove
        */
        $scope.removeLookupField = function (field) {
            $scope.draft({
                edit: true,
                values: {
                    path: 'indexes.{0}.$removeField'.format($scope.activeIndex),
                    advanced: true,
                    value: [parseInt(field, 10)]
                }
            }, function () {
                var i,
                    lookupFields = $scope.advancedSettings.indexes[$scope.activeIndex].lookupFields;
                for (i = lookupFields.length - 1; i >= 0; i -= 1) {
                    if (lookupFields[i].id === field) {
                        lookupFields.splice(i, 1);
                        break;
                    }
                }
                $scope.setAvailableFields();
            });
        };

        /**
        * Checks if user can still add more lookup fields.
        *
        * @return {boolean} False if all available fields have already been selected
        *                   or the amount of added fields is equal to amount of all fields for
        *                   that object. True otherwise.
        */
        $scope.canAddLookupFields = function () {
            return $scope.activeIndex !== -1
                            && $scope.availableFields.length > 0
                            && $scope.lookup.lookupFields.length < $scope.fields.length;
        };

        /**
        * Checks if there are fields selected to move left in REST view.
        */
        $scope.canMoveLeftRest = function() {
             return $('.target-item.rest-fields.selected').size() > 0;
        };

        /**
        * Checks if there are fields to move left in REST view.
        */
        $scope.canMoveAllLeftRest = function() {
            return $scope.selectedEntityAdvancedFields.length > 0;
        };

        /**
        * Checks if there are fields selected to move right in REST view.
        */
        $scope.canMoveRightRest = function() {
             return $('.source-item.rest-fields.selected').size() > 0;
        };

        /**
        * Checks if there are fields to move right in REST view.
        */
        $scope.canMoveAllRightRest = function() {
            return $scope.selectedEntityAdvancedAvailableFields.length > 0;
        };

        /* VALIDATION FUNCTIONS */

        /**
        * Checks if criterion value is valid
        * @param {object} criterion to validate
        * @param {object} list containing all field's validation criteria
        * @return {string} empty if criterion is valid (otherwise contains validation error)
        */
        $scope.validateCriterion = function(criterion, validationCriteria) {
            var anotherCriterion;

            if (criterion.enabled) {
                if (criterion.value === null || criterion.value.length === 0) {
                    return 'mds.error.requiredField';
                }

                switch (criterion.displayName) {
                    case 'mds.field.validation.minLength':
                        if (criterion.value < 0) {
                            return 'mds.error.lengthMustBePositive';
                        } else {
                            anotherCriterion = $scope.findCriterionByName(validationCriteria, 'mds.field.validation.maxLength');

                            if (anotherCriterion !== null && anotherCriterion.enabled && anotherCriterion.value
                                && anotherCriterion.value < criterion.value) {
                                    return 'mds.error.minCannotBeBigger';
                            }
                        }
                        break;
                    case 'mds.field.validation.maxLength':
                        if (criterion.value < 0) {
                            return 'mds.error.lengthMustBePositive';
                        } else {
                            anotherCriterion = $scope.findCriterionByName(validationCriteria, 'mds.field.validation.minLength');

                            if (anotherCriterion !== null && anotherCriterion.enabled && anotherCriterion.value
                                && anotherCriterion.value > criterion.value) {
                                    return 'mds.error.maxCannotBeSmaller';
                            }
                        }
                        break;
                    case 'mds.field.validation.minValue':
                        anotherCriterion = $scope.findCriterionByName(validationCriteria, 'mds.field.validation.maxValue');

                        if (anotherCriterion !== null && anotherCriterion.enabled && anotherCriterion.value
                            && anotherCriterion.value < criterion.value) {
                                return 'mds.error.minCannotBeBigger';
                        }
                        break;
                    case 'mds.field.validation.maxValue':
                        anotherCriterion = $scope.findCriterionByName(validationCriteria, 'mds.field.validation.minValue');

                        if (anotherCriterion !== null && anotherCriterion.enabled && anotherCriterion.value
                            && anotherCriterion.value > criterion.value) {
                                return 'mds.error.maxCannotBeSmaller';
                        }
                        break;
                }
            }

            return '';
        };

        /* BROWSING FUNCTIONS */

        /**
        * Checks if field is filterable.
        */
        $scope.isFilterable = function(field) {
            if ($scope.filterableTypes.indexOf(field.type.displayName) < 0) {
                return false;
            } else {
                return true;
            }
        };

        /**
        * Function called each time when user changes the checkbox state on 'Browsing settings' view.
        * Responsible for updating the model.
        */
        $scope.onFilterableChange = function(field) {
            var selected = $scope.advancedSettings.browsing.filterableFields.indexOf(field.id);

            $scope.draft({
                edit: true,
                values: {
                    path: 'browsing.${0}'.format(selected ? 'addFilterableField' : 'removeFilterableField'),
                    advanced: true,
                    value: [field.id]
                }
            }, function () {
                if(selected) {
                    $scope.advancedSettings.browsing.filterableFields.push(field.id);
                } else {
                    $scope.advancedSettings.browsing.filterableFields.removeObject(field.id);
                }
            });
        };

        /**
        * Callback function called each time when user adds, removes or moves items in 'Displayed Fields' on
        * 'Browsing Settings' view. Responsible for updating the model.
        */
        $scope.onDisplayedChange = function(container) {
            $scope.advancedSettings.browsing.displayedFields = [];

            angular.forEach(container, function(field) {
                $scope.advancedSettings.browsing.displayedFields.push(field.id);
            });

            $scope.draft({
                edit: true,
                values: {
                    path: 'browsing.$setDisplayedFields',
                    advanced: true,
                    value: [$scope.advancedSettings.browsing.displayedFields]
                }
            });
        };

        /**
        * Function moving "Fields to display" item up (in model).
        */
        $scope.targetItemMoveUp = function(index) {
            var tmp;
            if (index > 0) {
                tmp = $scope.browsingDisplayed[index];
                $scope.browsingDisplayed[index] = $scope.browsingDisplayed[index - 1];
                $scope.browsingDisplayed[index - 1] = tmp;
            }
        };

        /**
        * Function moving "Fields to display" item down (in model).
        */
        $scope.targetItemMoveDown = function(index) {
            var tmp;
            if (index < $scope.browsingDisplayed.length - 1) {
                tmp = $scope.browsingDisplayed[index + 1];
                $scope.browsingDisplayed[index + 1] = $scope.browsingDisplayed[index];
                $scope.browsingDisplayed[index] = tmp;
            }
        };

        /**
        * Function moving selected "Fields to display" items up (in model).
        */
        $scope.itemsUp = function() {
            var items = $(".connected-list-target.browsing").children(),
                indices = [],
                firstUnselectedIndex = parseInt(items.filter(':not(.selected)').first().attr('item-index'),10),
                selected = {},
                array = [];

            items.filter('.selected').each(function() {
                var item = $(this),
                index =  parseInt($(item).attr('item-index'), 10);
                    // save 'selected' state
                    selected[$scope.browsingDisplayed[index].id] = true;
                    if(firstUnselectedIndex < index) {
                        indices.push(index);
                    }
            });

            angular.forEach(indices, function(index) {
                $scope.targetItemMoveUp(index);
            });

            angular.forEach($scope.browsingDisplayed, function (item) {
                array.push(item.id);
            });

            $scope.draft({
                edit: true,
                values: {
                    path: 'browsing.$setDisplayedFields',
                    advanced: true,
                    value: [array]
                }
            }, function () {
                // restore 'selected' state
                $timeout(function() {
                    $(".connected-list-target.browsing").children().each(function(index) {
                        if(selected[$scope.browsingDisplayed[index].id]) {
                            $(this).addClass('selected');
                        }
                    });
                });
            });
        };

        /**
        * Function moving selected "Fields to display" items down (in model).
        */
        $scope.itemsDown = function() {
            var items = $(".connected-list-target.browsing").children(),
                indices = [],
                lastUnselectedIndex = parseInt(items.filter(':not(.selected)').last().attr('item-index'),10),
                selected = {},
                array = [];

            items.filter('.selected').each(function() {
                var item = $(this),
                index =  parseInt($(item).attr('item-index'), 10);
                // save 'selected' state
                selected[$scope.browsingDisplayed[index].id] = true;
                if(lastUnselectedIndex > index) {
                    indices.push(index);
                }
            });

            angular.forEach(indices.reverse(), function(index) {
                $scope.targetItemMoveDown(index);
            });

            angular.forEach($scope.browsingDisplayed, function (item) {
                array.push(item.id);
            });

            $scope.draft({
                edit: true,
                values: {
                    path: 'browsing.$setDisplayedFields',
                    advanced: true,
                    value: [array]
                }
            }, function () {
                // restore 'selected' state
                $timeout(function() {
                    $(".connected-list-target.browsing").children().each(function(index) {
                        if(selected[$scope.browsingDisplayed[index].id]) {
                            $(this).addClass('selected');
                        }
                    });
                });
            });
        };

        /**
        * Checks if there are fields allowed to move up in 'Browsing Settings' view.
        */
        $scope.canMoveUp = function() {
            var items = $('.target-item.browsing'),
                wasLastSelected = true,
                ret = false;
            if (items.filter('.selected').size() === 0) {
                return false;
            }
            items.each(function() {
                var isThisSelected = $(this).hasClass('selected');
                if (!wasLastSelected && isThisSelected) {
                    ret = true;
                }
                wasLastSelected = isThisSelected;
            });
            return ret;
        };

        /**
        * Checks if there are fields allowed to move up in 'Browsing Settings' view.
        */
        $scope.canMoveDown = function() {
            var items = $('.target-item.browsing'),
                wasLastSelected = true,
                ret = false;
            if (items.filter('.selected').size() === 0) {
                return false;
            }
            $(items.get().reverse()).each(function() {
                var isThisSelected = $(this).hasClass('selected');
                if (!wasLastSelected && isThisSelected) {
                    ret = true;
                }
                wasLastSelected = isThisSelected;
            });
            return ret;
        };

        /**
        * Checks if there are fields selected to move left in 'Browsing Settings' view.
        */
        $scope.canMoveLeft = function() {
             return $('.target-item.browsing.selected').size() > 0;
        };

        /**
        * Checks if there are fields to move left in 'Browsing Settings' view.
        */
        $scope.canMoveAllLeft = function() {
            return $scope.browsingDisplayed.length > 0;
        };

        /**
        * Checks if there are fields selected to move right in 'Browsing Settings' view.
        */
        $scope.canMoveRight = function() {
             return $('.source-item.browsing.selected').size() > 0;
        };

        /**
        * Checks if there are fields to move right in 'Browsing Settings' view.
        */
        $scope.canMoveAllRight = function() {
            return $scope.browsingAvailable.length > 0;
        };

        /* UTILITY FUNCTIONS */

        /**
        * Find all lookups with given name.
        *
        * @param {string} name This value will be used to find lookups.
        * @param {Array} array Array in which we're looking for name.
        * @return {Array} array of lookups with the given name.
        */
        $scope.findLookupByName = function (name, array) {
            var lookup = MDSUtils.find(array, [{ field: 'lookupName', value: name}], false, true);
            return $.isArray(lookup) ? lookup[0] : lookup;
        };

        /**
        * Find validation criterion with given name in field's validation criteria
        * @param {object} list of field's validation criteria
        * @param {string} name of criteria to be found
        * @return {object} found criterion with given name or null if no such criterion exists
        */
        $scope.findCriterionByName = function (validationCriteria, name) {
            var foundCriterion = null;
            angular.forEach(validationCriteria, function (criterion) {
                if (criterion.displayName === name) {
                    foundCriterion = criterion;
                }
            });

            return foundCriterion;
        };

        /**
        * Find unique field with given id.
        *
        * @param {string} id This value will be used to find fields.
        * @return {object} unique field with the given id.
        */
        $scope.findFieldById = function (id) {
            return MDSUtils.find($scope.fields, [{ field: 'id', value: id}], true);
        };

        /**
        * Find display name of the unique field with given id.
        *
        * @param {string} id This value will be used to find fields.
        * @return {object} display name of the unique field with the given id.
        */
        $scope.getFieldDisplayNameByFieldId = function (id) {
            var fields = $scope.fields.valueOf(), result, i;

            for (i = 0; i < fields.length; i += 1) {
                //toString() method is called because sometimes id has a numeric type
                if (id.toString() === fields[i].id.toString()) {
                    result = fields[i].basic.displayName;
                    break;
                }
            }

            return result;
        };

        /**
        * Find all fields with given id.
        *
        * @param {string} id This value will be used to find fields.
        * @param {Array} array Array in which we're looking for id.
        * @return {Array} array of fields with the given id.
        */
        $scope.findFieldInArrayById = function (id, array) {
            var field = MDSUtils.find(array, [{ field: 'id', value: id}], false);
            return $.isArray(field) ? field[0] : field;
        };

        /**
        * Find all fields with given name.
        *
        * @param {string} name This value will be used to find fields.
        * @return {Array} array of fields with the given name.
        */
        $scope.findFieldsByName = function (name) {
            return MDSUtils.find($scope.fields, [{ field: 'basic.name', value: name}], false, true);
        };

        /**
        * Find validation criterion with given name in field's validation criteria.
        *
        * @param {object} validationCriteria list of field's validation criteria.
        * @param {string} name The name of criteria to be found.
        * @return {object} found criterion with given name or null if no such criterion exists.
        */
        $scope.findCriterionByName = function (validationCriteria, name) {
            return MDSUtils.find(validationCriteria, [{ field: 'displayName', value: name}], true);
        };

        /**
        * Find field setting with given name.
        *
        * @param {Array} settings A array of field settings.
        * @param {string} name This value will be used to find setting.
        * @return {object} a single object which represent setting with the given name.
        */
        $scope.findSettingByName = function (settings, name) {
            return MDSUtils.find(settings, [{field: 'name', value: name}], true);
        };

        /*
        * Gets type information from TypeDto object.

        * @param {object} typeObject TypeDto object containing type information
        * @return {string} type information taken from parameter object.
        */
        $scope.getTypeSingleClassName = function (type) {
            return type.displayName.substring(type.displayName.lastIndexOf('.') + 1);
        };

        /**
        * Construct appropriate url according with a field type for form used to set correct
        * value of default value property.
        *
        * @param {string} type The type of a field.
        * @return {string} url to appropriate form.
        */
        $scope.loadDefaultValueForm = function (type) {
            var value = $scope.getTypeSingleClassName(type);

            return '../mds/resources/partials/widgets/field-basic-defaultValue-{0}.html'
                .format(value.substring(value.toLowerCase()));
        };

        /**
        * Check if the given number has appropriate precision and scale.
        *
        * @param {number} number The number to validate.
        * @param {object} settings Object with precision and scale properties.
        * @return {boolean} true if number has appropriate precision and scale or it is undefined;
        *                   otherwise false.
        */
        $scope.checkDecimalValue = function (number, settings) {
            var precision = $scope.findSettingByName(settings, 'mds.form.label.precision'),
                scale = $scope.findSettingByName(settings, 'mds.form.label.scale');

            return _.isNumber(number)
                ? MDSUtils.validateDecimal(number, precision.value, scale.value)
                : true;
        };

        /**
        * Return message in correct language that inform user the decimal value has incorrect
        * precision and/or scale.
        *
        * @param {Array} settings A array of field settings.
        * @return {string} A appropriate error message.
        */
        $scope.getInvalidDecimalMessage = function (settings) {
            var precision = $scope.findSettingByName(settings, 'mds.form.label.precision'),
                scale = $scope.findSettingByName(settings, 'mds.form.label.scale');

            return $scope.msg('mds.error.incorrectDecimalValue', precision.value, scale.value);
        };

        /**
        * Return available values for combobox field.
        *
        * @param {Array} setting A array of field settings.
        * @return {Array} A array of possible combobox values.
        */
        $scope.getComboboxValues = function (settings) {
            return MDSUtils.find(settings, [{field: 'name', value: 'mds.form.label.values'}], true).value;
        };

        /**
        * Check that all options in the given setting are valid.
        *
        * @param {object} setting The given setting to check
        * @return {boolean} true if all options are valid; otherwise false.
        */
        $scope.checkOptions = function (setting) {
            var expression = true;

            angular.forEach(setting.options, function (option) {
                switch (option) {
                case 'REQUIRE':
                    expression = expression && $scope.hasValue(setting);
                    break;
                case 'POSITIVE':
                    expression = expression && $scope.hasPositiveValue(setting);
                    break;
                default:
                    break;
                }
            });

            return expression;
        };

        /**
        * Check if the given setting has a option with the given name.
        *
        * @param {object} setting The setting to check.
        * @param {string} name Option name.
        * @return {boolean} true if the setting has a option; otherwise false.
        */
        $scope.hasOption = function (setting, name) {
            return setting.options && $.inArray(name, setting.options) !== -1;
        };

        /**
        * Check if the given setting has a value.
        *
        * @param {object} setting The setting to check.
        * @return {boolean} true if the setting has a value; otherwise false.
        */
        $scope.hasValue = function (setting) {
            return setting
                && !_.isNull(setting.value)
                && !_.isUndefined(setting.value)
                && (_.isArray(setting.value) ? setting.value.length > 0 : true);
        };

        /**
        * Check if the given setting has a positive value.
        *
        * @param {object} setting The setting to check.
        * @return {boolean} true if the setting has a positive value; otherwise false.
        */
        $scope.hasPositiveValue = function (setting) {
            return $scope.hasValue(setting) && _.isNumber(setting.value) && setting.value >= 0;
        };

        /**
        * Check if the given setting has a given type.
        *
        * @param {object} setting The setting to check.
        * @param {string} type The given type.
        * @return {boolean} true if the setting has a given type; otherwise false.
        */
        $scope.hasType = function (setting, type) {
            var fullName, singleName;

            if (!setting.type) {
                return false;
            } else {
                fullName = setting.type.typeClass;
                singleName = fullName.substring(fullName.lastIndexOf('.') + 1);

                return _.isEqual(singleName.toLowerCase(), type.toLowerCase());
            }
        };

        /**
        * Set an additional watcher for $scope.selectedEntity. Its role is to get fields related to
        * the entity in situation in which the entity was selected from the entity list.
        */
        $scope.$watch('selectedEntity', function () {
            blockUI();

            if ($scope.selectedEntity && $scope.selectedEntity.id) {
                workInProgress.setActualEntity(Entities, $scope.selectedEntity.id);

                $scope.fields = Entities.getFields({id: $scope.selectedEntity.id}, function () {
                    setSecuritySettings();
                    setAdvancedSettings();
                });

                unblockUI();
            } else {
                workInProgress.setActualEntity(Entities, undefined);

                delete $scope.fields;
                delete $scope.advancedSettings;
                unblockUI();
            }
        });

        /**
        * Set an additional watcher for $scope.newField.type. Its role is to set name for created
        * field using defaultName property from the selected field type but only if a user did not
        * enter name. If field with name equal to value of defaultName property already exists,
        * the unique id will be added to end of created field name.
        */
        $scope.$watch('newField.type', function () {
            var found;

            if (isBlank($scope.newField.name) && $scope.newField.type) {
                found = $scope.findFieldsByName($scope.newField.type.defaultName);

                $scope.newField.name = $scope.newField.type.defaultName;

                if (found.length !== 0) {
                    $scope.newField.name += '{0}'.format(_.uniqueId());
                }
            }
        });

        /* ~~~~~ SECURITY FUNCTIONS ~~~~~ */

        /**
        * Gets a class for 'Security' view toggle button based on entity access option
        */
        $scope.getClass = function(access) {
            if ($scope.securitySettings === null || $scope.securitySettings.securityMode !== access) {
                return 'btn btn-default';
            } else {
                return 'btn btn-success';
            }
        };

        /**
        * Callback function called when entity access option changes
        */
        $scope.accessChanged = function(access) {
            if (access !== 'USERS') {
                $scope.clearUsers();
            }

            if (access !== 'ROLES') {
                $scope.clearRoles();
            }

            $scope.draft({
                edit: true,
                values: {
                    path: "$accessChanged",
                    value: [access]
                }
            }, function () {
                $scope.securitySettings.securityMode = access;
            });
        };

        /**
        * Clears roles list in 'Security' view
        */
        $scope.commitSecurity = function() {
            $scope.securityList = [];

            if ($scope.securitySettings.securityMode === 'USERS') {
                $scope.clearRoles();
                $scope.securityList = $scope.securitySettings.users;
            } else if ($scope.securitySettings.securityMode === 'ROLES') {
                $scope.clearUsers();
                $scope.securityList = $scope.securitySettings.roles;
            } else {
                $scope.clearUsers();
                $scope.clearRoles();
            }

            $scope.draft({
                edit: true,
                values: {
                    path: "$securitySave",
                    security: true,
                    value: [$scope.securitySettings.securityMode, $scope.securityList]
                }
            });
        };

        /**
        * Clears user list in 'Security' view
        */
        $scope.clearUsers = function() {
            $scope.securitySettings.users = [];
            $('#usersSelect').select2('val', $scope.securitySettings.users);
        };

        /**
        * Clears roles list in 'Security' view
        */
        $scope.clearRoles = function() {
            $scope.securitySettings.roles = [];
            $('#rolesSelect').select2('val', $scope.securitySettings.roles);
        };

        /**
        * Callback function called when users list under 'Security' view changes
        */
        $scope.usersChanged = function(change) {
            var value;

            if (change.added) {
                value = change.added.text;
                $scope.securitySettings.users.push(value);
            } else if (change.removed) {
                value = change.removed.text;
                $scope.securitySettings.users.removeObject(value);
            }
        };

        /**
        * Callback function called when roles list under 'Security' view changes
        */
        $scope.rolesChanged = function(change) {
            var value;

            if (change.added) {
                value = change.added.text;
                $scope.securitySettings.roles.push(value);
            } else if (change.removed) {
                value = change.removed.text;
                $scope.securitySettings.roles.removeObject(value);
            }
        };
    });

    /**
    * The DataBrowserCtrl controller is used on the 'Data Browser' view.
    */
    controllers.controller('DataBrowserCtrl', function ($rootScope, $scope, $http, Entities, Instances, History,
                                $timeout, MDSUtils, Locale) {
        workInProgress.setActualEntity(Entities, undefined);

        $scope.modificationFields = ['modificationDate', 'modifiedBy'];

        /**
        * An array perisisting currently hidden modules in data browser view
        */
        $scope.hidden = [];

        /**
        * A map containing names of all entities in MDS, indexed by module names
        */
        $scope.modules = {};

        /**
        * This variable is set after user clicks "View" button next to chosen entity
        */
        $scope.selectedEntity = undefined;

        $scope.selectedFields = [];

        /**
        * Fields that belong to a certain lookup
        */
        $scope.lookupFields = [];

        /**
        * Object that represents selected lookup options
        */
        $scope.lookupBy = {};

        /**
        * Object that represents selected filter
        */
        $scope.filterBy = {};

        /**
        * This variable is set after user clicks "Add" button next to chosen entity.
        */
        $scope.addedEntity = undefined;

        /**
        * This variable is set after user choose Entity in instance view.
        */
        $scope.selectedInstance = undefined;

        /**
        * This variable is set after user clicks "History" button in entity detail view.
        */
        $scope.instanceId = undefined;

        /**
        * This variable is set after user choose field in history instance view.
        */
        $scope.previousInstance = undefined;

        /**
        * An array of selected instance fields.
        */
        $scope.loadedFields = [];

        $scope.currentRecord = undefined;

        $scope.allEntityFields = [];

        $scope.validatePattern = '';

        $scope.optionValue = '';

        $rootScope.filters = [];

        $scope.instanceEditMode = false;

        $scope.showTrash = false;

        $scope.showFilters = true;

        $scope.showTrashInstance = false;

        $scope.instanceRevertable = false;

        $scope.availableLocale = Locale.get();

        $scope.selectedFieldId = 0;

        // fields which won't be persisted in the user cookie
        $scope.autoDisplayFields = [];

        /**
        * Check if there are any entities to display
        */
        $scope.areModulesEmpty = function (map) {
            if (map && Object.keys(map).length > 0) {
                return false;
            } else {
                return true;
            }
        };

        /**
        * Initializes a map of all entities in MDS indexed by module name
        */
        $scope.setEntities = function () {
            blockUI();
            $http.get('../mds/entities/byModule').success(function (data) {
                $scope.modules = data;
                unblockUI();
            });
        };

        /**
        * Sets module and entity name
        */
        $scope.setModuleEntity = function (module, entityName) {
            $scope.tmpModuleName = module;
            $scope.tmpEntityName = entityName;
        };

        /**
        * Sets visible filters panel when filter exist
        */
        $scope.setVisibleIfExistFilters = function () {
            if ($scope.entityAdvanced !== undefined && $scope.entityAdvanced !== null
                && $scope.entityAdvanced.browsing.filterableFields !== null
                && $scope.entityAdvanced.browsing.filterableFields.length > 0) {
                $scope.showFilters = true;
                innerLayout({
                    spacing_closed: 30,
                    east__minSize: 200,
                    east__maxSize: 350
                }, {
                    show: true,
                    button: '#mds-filters'
                });
                } else {
                    $scope.showFilters = false;
                    innerLayout({
                        spacing_closed: 30,
                        east__minSize: 200,
                        east__maxSize: 350
                    }, {
                    show: false
                });
            }
        };

        /**
        * Sets hidden filters panel
        */
        $scope.setHiddenFilters = function () {
            $scope.showFilters = false;
                innerLayout({
                    spacing_closed: 30,
                    east__minSize: 200,
                    east__maxSize: 350
                }, {
                show: false
            });
        };

        /**
        * Sets selected entity by module and entity name
        */
        $scope.addInstance = function(module, entityName) {
            blockUI();
            $scope.instanceEditMode = false;
            if (typeof module === 'undefined') {
                if ($scope.selectedEntity.module === null) {
                    module = '(No module)';
                } else {
                    module = $scope.selectedEntity.module;
                }
            }
            if (typeof entityName === 'undefined') {
                entityName = $scope.selectedEntity.name;
            }
            $scope.setModuleEntity(module, entityName);
            $scope.addedEntity = Entities.getEntity({
                param:  module,
                params: entityName},
                function () {
                    Instances.newInstance({id: $scope.addedEntity.id}, function(data) {
                        $scope.currentRecord = data;
                        $scope.fields = data.fields;
                        unblockUI();
                    });
                });
        };

        /**
        * Sets selected entity by module and entity name
        */
        $scope.editInstance = function(id) {
            blockUI();
            $scope.setHiddenFilters();
            $scope.instanceEditMode = true;
            $scope.loadedFields = Instances.selectInstance({
                id: $scope.selectedEntity.id,
                param: id
                },
                function (data) {
                    $scope.selectedInstance = id;
                    $scope.currentRecord = data;
                    $scope.fields = data.fields;
                    unblockUI();
                }, angularHandler('mds.error', 'mds.error.cannotUpdateInstance'));
        };

        $scope.editInstanceOfEntity = function(instanceId, entityClassName) {
            $scope.selectEntityByClassName(entityClassName, function() {
                $scope.editInstance(instanceId);
            });
        };

        $scope.downloadBlob = function(fieldName) {
            $http.get('../mds/instances/' + $scope.selectedEntity.id + '/' + $scope.selectedInstance + '/' + fieldName)
            .success(function (data) {
                window.location.replace("../mds/instances/" + $scope.selectedEntity.id + "/" + $scope.selectedInstance + "/" + fieldName);
            })
            .error(alertHandler('mds.error', 'mds.error.cannotDownloadBlob'));
        };

        $scope.deleteBlobContent = function() {
            blockUI();
            $http.get('../mds/instances/deleteBlob/' + $scope.selectedEntity.id + '/' + $scope.selectedInstance + '/' + $scope.selectedFieldId)
            .success(alertHandler('mds.success', 'mds.delete.deleteBlobContent.success'))
            .error(alertHandler('mds.error', 'mds.error.cannotDeleteBlobContent'));
        };

        $scope.selectField = function (fieldId) {
            $scope.selectedFieldId = fieldId;
        };

        /**
        * Sets loadedFields from previous instance and determines whether this version
        * of the instance is revertable.
        */
        $scope.historyInstance = function(id) {
            blockUI();
            if($scope.selectedEntity !== null) {
            $scope.loadedFields = History.getPreviousVersion(
                {
                entityId: $scope.selectedEntity.id,
                instanceId: $scope.selectedInstance,
                param: id},
                function (data) {
                    $scope.previousInstance = id;
                    $scope.fields = data.fields;
                    $scope.instanceRevertable = data.revertable;
                    unblockUI();
                });
            }
        };

        $scope.revertPreviousVersion = function() {
           blockUI();
           if($scope.selectedEntity !== null) {
               $scope.loadedFields = History.revertPreviousVersion(
               {
                   entityId: $scope.selectedEntity.id,
                   instanceId: $scope.selectedInstance,
                   param: $scope.previousInstance
               },
               function (data) {
                   $scope.previousInstance = undefined;
                   unblockUI();
               }, angularHandler('mds.error', 'mds.error.cannotRevert'));
           }
        };

        $scope.revertVersionButton = function() {
            if (!$scope.instanceRevertable) {
                return "disabled";
            } else {
                return "";
            }
        };

        /**
        * Revert selected instance from trash
        */
        $scope.revertFromTrash = function(selected) {
            blockUI();
            $scope.setVisibleIfExistFilters();
            $scope.loadedFields = Instances.revertInstanceFromTrash({
                id: $scope.selectedEntity.id,
                param: selected
            }, function() {
               unblockUI();
               $scope.selectedInstance = undefined;
               $scope.previousInstance = undefined;
               $scope.showTrashInstance = false;
            });
        };

        /**
        * Get selected instance from trash
        */
        $scope.trashInstance = function(id) {
            blockUI();
            if($scope.selectedEntity !== null) {
                $scope.instanceEditMode = true;
                $http.get('../mds/entities/' + $scope.selectedEntity.id + '/trash/' + id)
                    .success(function (data) {
                        $scope.setVisibleIfExistFilters();
                        $scope.showTrashInstance = true;
                        $scope.previousInstance = id;
                        $scope.selectedInstance = id;
                        unblockUI();
                    }
                );
            }
        };

        /**
        * Unselects adding or editing instance to allow user to return to entities list by modules
        */
        $scope.unselectInstance = function() {
            $scope.selectEntity($scope.tmpModuleName, $scope.tmpEntityName);
            $scope.addedEntity = undefined;
            $scope.selectedInstance = undefined;
            $scope.loadedFields = undefined;
            innerLayout({
                spacing_closed: 30,
                east__minSize: 200,
                east__maxSize: 350
            });
        };

        /**
        *
        * Saves the entity instance after the user clicks save
        *
        */
        $scope.addEntityInstance = function () {
            blockUI();
            $scope.currentRecord.$save(function() {
                $scope.unselectInstance();
                unblockUI();
            }, angularHandler('mds.error', 'mds.error.cannotAddInstance'));
        };

        /**
        * Deletes an instance of the currently selected entity, with id "selected".
        */
        $scope.deleteInstance = function (selected) {
            blockUI();
            Instances.deleteInstance({
                id: $scope.selectedEntity.id,
                param: selected
            }, function() {
                $scope.unselectInstance();
                unblockUI();
            }, angularHandler('mds.error', 'mds.error.cannotDeleteInstance'));
        };

        /**
        * Find field setting with given name.
        *
        * @param {Array} settings A array of field settings.
        * @param {string} name This value will be used to find setting.
        * @return {object} a single object which represent setting with the given name.
        */
        $scope.findSettingByName = function (settings, name) {
            return MDSUtils.find(settings, [{field: 'name', value: name}], true);
        };

        /**
        * Sets selected instance history by id
        */
        $scope.selectInstanceHistory = function (instanceId) {
            blockUI();
            History.getHistory({
                entityId: $scope.selectedEntity.id,
                instanceId: instanceId
            }, function () {
                unblockUI();
                $scope.previousInstance = undefined;
                $scope.instanceId = instanceId;
            }, angularHandler('mds.error', 'mds.error.historyRetrievalError'));
        };

        $scope.backToInstance = function() {
            $scope.unselectInstanceHistory();
            $scope.editInstance($scope.selectedInstance);
            $scope.updateInstanceGridFields();
        };

        /**
        * Unselects instace history to allow user to return to instance view
        */
        $scope.unselectInstanceHistory = function() {
            // Temporary - should return to instance view
            $scope.instanceId = undefined;
            $scope.previousInstance = undefined;
            $scope.updateInstanceGridFields();
        };

        /**
        * Select view trash
        */
        $scope.showInstancesTrash = function () {
            $scope.showTrash = true;
            $scope.setHiddenFilters();
            $scope.showTrashInstance = false;
            $scope.updateInstanceGridFields($scope.modificationFields);
        };

        /**
        * Select view entity instance list
        */
        $scope.hideInstancesTrash = function () {
            $scope.showTrash = false;
            $scope.setVisibleIfExistFilters();
            $scope.showTrashInstance = false;
            $scope.updateInstanceGridFields();
        };

        /**
        * Select view entity instances trash
        */
        $scope.backInstancesTrash = function () {
            $scope.showTrash = false;
            $scope.showTrashInstance = false;
            $scope.setHiddenFilters();
            $scope.selectedInstance = undefined;
            $scope.previousInstance = undefined;
            $scope.hideInstancesTrash();
            $scope.showInstancesTrash();
            $timeout(function() {
                $scope.updateInstanceGridFields($scope.modificationFields);
            });
        };

        /**
        * Sets selected entity by module and entity name
        */
        $scope.selectEntity = function (module, entityName) {
            // get entity, fields, display fields
            $scope.retrieveAndSetEntityData('../mds/entities/getEntity/' + module + '/' + entityName);
        };

        /**
        * Sets selected entity by the entities className
        */
        $scope.selectEntityByClassName = function(entityClassName, callback) {
            // get entity, fields, display fields
            $scope.retrieveAndSetEntityData('../mds/entities/getEntityByClassName?entityClassName=' + entityClassName,
                callback);
        };

        $scope.retrieveAndSetEntityData = function(entityUrl, callback) {
          $scope.lookupBy = {};
          $scope.selectedLookup = undefined;
          $scope.lookupFields = [];
          $scope.allEntityFields = [];

          blockUI();

          $http.get(entityUrl).success(function (data) {
              $scope.selectedEntity = data;

              $scope.setModuleEntity($scope.selectedEntity.module, $scope.selectedEntity.name);

              $http.get('../mds/entities/'+$scope.selectedEntity.id+'/entityFields').success(function (data) {
                   $scope.allEntityFields = data;

                   Entities.getAdvancedCommited({id: $scope.selectedEntity.id}, function(data) {
                      $scope.entityAdvanced = data;
                      $rootScope.filters = [];
                      $scope.setVisibleIfExistFilters();

                      var filterableFields = $scope.entityAdvanced.browsing.filterableFields,
                          i, field, types;
                      for (i = 0; i < $scope.allEntityFields.length; i += 1) {
                          field = $scope.allEntityFields[i];

                          if ($.inArray(field.id, filterableFields) >= 0) {
                              types = $scope.filtersForField(field);

                              $rootScope.filters.push({
                                  field: field.basic.name,
                                  types: types
                              });
                          }
                      }
                   });

                   Entities.getDisplayFields({id: $scope.selectedEntity.id}, function(data) {
                        var i, field, selectedName,
                            dbUserPreferences = $scope.getDataBrowserUserPreferencesCookie($scope.selectedEntity);

                        $scope.selectedFields = [];

                        // filter data from db
                        for (i = 0; i < data.length; i += 1) {
                            field = data[i];
                            if ($.inArray(field.basic.name, dbUserPreferences.unselected) === -1) {
                                $scope.selectedFields.push(field);
                            }
                        }

                        // additional selections
                        for (i = 0; i < dbUserPreferences.selected.length; i += 1) {
                            selectedName = dbUserPreferences.selected[i];
                            // check if already selected
                            if (!$scope.isFieldSelected(selectedName)) {
                                $scope.selectFieldByName(selectedName);
                            }
                        }

                        $scope.updateInstanceGridFields();
                    });

                    if (callback) {
                        callback();
                    }
                });
                unblockUI();
            });
        };

        $scope.getMetadata = function(field, key) {
            var i, result = '';
            if (field.metadata) {
                for (i = 0; i < field.metadata.length; i += 1) {
                    if (_.isEqual(field.metadata[i].key, key)) {
                        result = field.metadata[i].value;
                    }
                }
            }
            return result;
        };

        $scope.isRelationshipField = function(field) {
            return Boolean($scope.getMetadata(field, 'related.class'));
        };

        $scope.isDateField = function(field) {
            return field.type.typeClass === "java.util.Date";
        };

        $scope.isTextArea = function (settings) {
            return MDSUtils.find(settings, [{field: 'name', value: 'mds.form.label.textarea'}], true).value;
        };

        $scope.dataBrowserPreferencesCookieName = function(entity) {
            var username = $rootScope.username || '';
            return username + '_org.motechproject.mds.databrowser.fields.' + entity.className + '#' + entity.id;
        };

        $scope.getDataBrowserUserPreferencesCookie = function(entity) {
            var cookieName = $scope.dataBrowserPreferencesCookieName($scope.selectedEntity),
                cookie;
            // get or create
            if ($.cookie(cookieName)) {
                cookie = JSON.parse($.cookie(cookieName));
            } else {
                cookie = {
                    selected: [],
                    unselected: []
                };
                $.cookie(cookieName, JSON.stringify(cookie));
            }

            // check fields
            if (cookie.unselected === undefined) {
                cookie.unselected = [];
            }
            if (cookie.selected === undefined) {
                cookie.selected = [];
            }

            return cookie;
        };

        $scope.isFieldSelected = function(name) {
            var i;
            for (i = 0; i < $scope.selectedFields.length; i += 1) {
                if ($scope.selectedFields[i].basic.name === name) {
                    return true;
                }
            }
            return false;
        };

        $scope.selectFieldByName = function(name) {
            var i, field;
            for (i = 0; i < $scope.allEntityFields.length; i += 1) {
                field = $scope.allEntityFields[i];
                if (field.basic.name === name) {
                    $scope.selectedFields.push(field);
                    return;
                }
            }
        };

        $scope.markFieldForDataBrowser = function(name, selected) {
            if (name) {
                var i, field, dbUserPreferences = $scope.getDataBrowserUserPreferencesCookie($scope.selectedEntity),
                    cookieName = $scope.dataBrowserPreferencesCookieName($scope.selectedEntity);

                if (selected) {
                    dbUserPreferences.unselected.removeObject(name);
                    dbUserPreferences.selected.uniquePush(name);

                    // update selectedFields for grid switch
                    if (!$scope.isFieldSelected(name)) {
                        for (i = 0; i < $scope.allEntityFields.length; i += 1) {
                            field = $scope.allEntityFields[i];
                            if (field.basic.name === name) {
                                $scope.selectedFields.push(field);
                            }
                        }
                    }
                } else {
                    dbUserPreferences.selected.removeObject(name);
                    dbUserPreferences.unselected.uniquePush(name);

                    // update selectedFields for grid switch
                    if ($scope.isFieldSelected(name)) {
                        for (i = 0; i < $scope.selectedFields.length; i += 1) {
                            field = $scope.selectedFields[i];
                            if (field.basic.name === name) {
                                $scope.selectedFields.remove(i, i + 1);
                            }
                        }
                    }
                }

                $.cookie(cookieName, JSON.stringify(dbUserPreferences));
            }
        };

        $scope.filtersForField = function(field) {
            var type = field.type.typeClass;
            if (type === "java.lang.Boolean") {
                return ['ALL', 'YES', 'NO'];
            } else if (type === "java.util.Date" || type === "org.joda.time.DateTime") {
                return ['ALL', 'TODAY', 'PAST_7_DAYS', 'THIS_MONTH', 'THIS_YEAR'];
            }
        };

        $rootScope.msgForFilter = function(filter) {
            return $scope.msg("mds.filter." + filter.toLowerCase());
        };

        $scope.getLookupIds = function(lookupFields) {
            var i, ids = [];
            for (i = 0; i < lookupFields.length; i += 1) {
                ids.push(lookupFields[i].id);
            }
            return ids;
        };

        /**
        * Marks passed lookup as selected. Sets fields that belong to the given lookup and resets lookupBy object
        * used to filter instances by given values
        */
        $scope.selectLookup = function(lookup) {
            var i;

            $scope.selectedLookup = lookup;
            $scope.lookupFields = [];
            $scope.filterBy = {};
            $scope.lookupBy = {};

            for(i=0; i<$scope.allEntityFields.length; i+=1) {
                if ($.inArray($scope.allEntityFields[i].id, $scope.getLookupIds($scope.selectedLookup.lookupFields)) !== -1) {
                    $scope.lookupFields.push($scope.allEntityFields[i]);
                }
            }
        };

        /**
        * Depending on the field type, includes proper html file containing visual representation for
        * the object type. Radio input for boolean, select input for list and text input as default one.
        */
        $scope.loadInputForLookupField = function(field) {
            var value = "default", type = "field", file;

            if (field.type.typeClass === "java.lang.Boolean") {
                value = "boolean";
            } else if (field.type.typeClass === "java.util.List") {
                value = "list";
            } else if (field.type.typeClass === "org.joda.time.DateTime") {
                value = "datetime";
            }

            if ($scope.isRangedLookup(field)) {
                type = 'range';
                if (!$scope.lookupBy[field.basic.name]) {
                    $scope.lookupBy[field.basic.name] = {min: '', max: ''};
                }
            } else if ($scope.isSetLookup(field)) {
                type = 'set';
                if (!$scope.lookupBy[field.basic.name]) {
                    $scope.lookupBy[field.basic.name] = [ {val: ''} ];
                }
            }

            return '../mds/resources/partials/widgets/lookups/{0}-{1}.html'
                .format(type, value);
        };

        $scope.isRangedLookup = function(field) {
            return $scope.isLookupFieldOfType(field, 'RANGE');
        };

        $scope.isSetLookup = function(field) {
            return $scope.isLookupFieldOfType(field, 'SET');
        };

        $scope.isLookupFieldOfType = function(field, type) {
            var i, lookupField;
            for (i = 0; i < $scope.selectedLookup.lookupFields.length; i += 1) {
                lookupField = $scope.selectedLookup.lookupFields[i];
                if (lookupField.name === field.basic.name) {
                    return lookupField.type === type;
                }
            }
        };

        $scope.addSetMember = function(set) {
            set.push({val: ''});
        };

        /**
        * Hides lookup dialog and sends signal to refresh the grid with new data
        */
        $scope.filterInstancesByLookup = function() {
            $scope.showLookupDialog();
            $scope.refreshGrid();
        };

        $scope.refreshGrid = function() {
            $scope.lookupRefresh = !$scope.lookupRefresh;
        };

        /**
        * Removes lookup and resets all fields associated with a lookup
        */
        $scope.removeLookup = function() {
            $scope.lookupBy = {};
            $scope.selectedLookup = undefined;
            $scope.lookupFields = [];
            $scope.filterInstancesByLookup();
        };

        /**
        * Unselects entity to allow user to return to entities list by modules
        */
        $scope.unselectEntity = function () {
            innerLayout({
                spacing_closed: 30,
                east__minSize: 200,
                east__maxSize: 350
            });
            $scope.selectedEntity = undefined;
        };

        $rootScope.selectFilter = function(field, filterType) {
            $scope.lookupBy = {};
            $scope.selectedLookup = undefined;
            $scope.lookupFields = [];

            $scope.filterBy = {
                field: field,
                type: filterType
            };

            blockUI();
            $scope.refreshGrid();
            unblockUI();
        };

        /**
        * Exports selected entity's instances to CSV file
        */
        $scope.exportEntityInstances = function() {
            $http.get("../mds/entities/" + $scope.selectedEntity.id + "/exportInstances")
            .success(function (data) {
                 window.location.replace("../mds/entities/" + $scope.selectedEntity.id + "/exportInstances");
            });
        };

        /**
        * Hides/Shows all entities under passed module name
        *
        * @param {string} module  Module name
        */
        $scope.collapse = function (module) {
            if ($.inArray(module, $scope.hidden) !== -1) {
                $scope.hidden.remove($scope.hidden.indexOf(module));
            } else {
                $scope.hidden.push(module);
            }
        };

        $scope.expandAll = function () {
            $scope.hidden.length = 0;
        };

        $scope.collapseAll = function () {
            angular.forEach($scope.modules, function (entities, module) {
                if ($scope.visible(module)) {
                    $scope.hidden.push(module);
                }
            });
        };



        /*
        *  Gets field from FieldRecord to set field-edit value
        */
        $scope.getFieldValue = function(name) {
            var myField = false;
            angular.forEach($scope.loadedFields,function(loadedField) {
                        if (_.isEqual(loadedField.displayName.toLowerCase(), name.toLowerCase())) {
                            myField = loadedField;
                            return loadedField;
                        }
            });
            return myField;
        };

        /*
        * Return string with information about CRUD action
        */
        $scope.getMsg = function(name,field) {
            var answer = "";
            angular.forEach(field.fields,function(row) {
                    if(_.isEqual(name,row.name )) {
                        answer = row.value;
                        return row.value;
                    }
            });
            return answer;
        };

        $scope.getInstanceFieldValue = function(fieldName) {
            var i, requestedField, val, field;
            if ($scope.fields) {
                for (i = 0; i < $scope.fields.length; i += 1) {
                    field = $scope.fields[i];

                    if (field.name === fieldName) {
                        requestedField = field;
                        break;
                    }
                }
            }
            if (requestedField) {
                val = requestedField.value;
            }
            return val;
        };

        /*
        * Gets validation criteria values.
        */
        $scope.getValidationCriteria = function (field, id) {
            var validationCriteria = '',
                value = $scope.getTypeSingleClassName(field.type);

            if (value === 'decimal' && id < 0) {
                validationCriteria = '/^(([0-9]{1,})|([0-9]{1,}(\\.([0-9]{1,}))))+$/';
            } else if (field.validation !== null && field.validation.criteria[id].enabled) {
                if (value === 'string' && field.validation.criteria[id].value.length > 0) {
                    validationCriteria = '/' + field.validation.criteria[id].value + '/';
                } else {
                    validationCriteria = field.validation.criteria[id].value;
                }
            }
            return validationCriteria;
        };

        /**
        * Construct appropriate url according with a field type for form used to set correct
        * value of edit value property.
        */
        $scope.loadEditValueForm = function (field) {
            var value = $scope.getTypeSingleClassName(field.type);
            if (value === 'combobox' && field.settings[2].value) {
                if (MDSUtils.find(field.settings, [{field: 'name', value: 'mds.form.label.allowMultipleSelections'}], true).value) {
                    value = 'combobox-multi';
                }
            }
            return '../mds/resources/partials/widgets/field-edit-Value-{0}.html'
                          .format(value.substring(value.toLowerCase()));
        };

        /*
        * Gets type information from TypeDto object.
        */
        $scope.getTypeSingleClassName = function (type) {
            return type.displayName.substring(type.displayName.lastIndexOf('.') + 1);
        };

        /**
        * Return available values for combobox field.
        *
        * @param {Array} setting A array of field settings.
        * @return {Array} A array of possible combobox values.
        */
        $scope.getComboboxValues = function (settings) {
            return MDSUtils.find(settings, [{field: 'name', value: 'mds.form.label.values'}], true).value;
        };

        /**
        * Checks if entities belonging to certain module are currently visible
        *
        * @param {string} module  Module name
        * @return {boolean} true if entities for given module name are visible, false otherwise
        */
        $scope.visible = function (module) {
            return $.inArray(module, $scope.hidden) !== -1 ? false : true;
        };

        /**
        * Check if the given number has appropriate precision and scale.
        *
        * @param {number} number The number to validate.
        * @param {object} settings Object with precision and scale properties.
        * @return {boolean} true if number has appropriate precision and scale or it is undefined;
        *                   otherwise false.
        */
        $scope.checkDecimalValue = function (numberInput, settings) {
            var precision = $scope.findSettingByName(settings, 'mds.form.label.precision'),
                scale = $scope.findSettingByName(settings, 'mds.form.label.scale'),
                number = parseFloat(numberInput);

            return _.isNumber(number)
                ? MDSUtils.validateDecimal(number, precision.value, scale.value)
                : true;
        };

        /**
        * Return message in correct language that inform user the decimal value has incorrect
        * precision and/or scale.
        *
        * @param {Array} settings A array of field settings.
        * @return {string} A appropriate error message.
        */
        $scope.getInvalidDecimalMessage = function (settings) {
            var precision = $scope.findSettingByName(settings, 'mds.form.label.precision'),
                scale = $scope.findSettingByName(settings, 'mds.form.label.scale');

            return $scope.msg('mds.error.incorrectDecimalValue', precision.value, scale.value);
        };

        $scope.arrow = function (module) {
            return $scope.visible(module) ? "icon-caret-down" : "icon-caret-right";
        };

        /**
        * Shows/Hides lookup dialog
        */
        $scope.showLookupDialog = function() {
            $("#lookup-dialog")
            .css({'top': ($("#lookupDialogButton").offset().top - $("#main-content").offset().top)-40,
            'left': ($("#lookupDialogButton").offset().left - $("#main-content").offset().left)-70})
            .toggle();
        };

        $scope.isAutoGenerated = function (field) {
            return hasMetadata(field, 'autoGenerated', 'true');
        };

        $scope.shouldShowInputForField = function(field) {
            return !$scope.isAutoGenerated(field);
        };

        /**
        * Handles hiding lookup dialog while clicking outside the dialog
        */
        $(document).mouseup(function (e) {
            var container = $("#lookup-dialog"),
            button = $("#lookupDialogButton");

            if (!container.is(e.target) && container.has(e.target).length === 0 &&
                !button.is(e.target) && button.has(e.target).length === 0 && container.is(":visible")) {
                if (e.target.offsetParent.hasAttribute("id") && e.target.offsetParent.id !== "ui-datepicker-div") {
                    $scope.showLookupDialog();
                }
            }
        });

        $scope.getFieldName = function(displayName) {
            var result;
            angular.forEach($scope.allEntityFields, function(field) {
                if (field.basic.displayName === displayName) {
                    result = field.basic.name;
                }
            });
            return result;
        };

        $scope.updateInstanceGridFields = function(fieldsToSelect) {
            angular.forEach($("select.multiselect")[0], function(field) {
                var name = $scope.getFieldName(field.label), selected = false;

                // this fields won't be used for cookie data
                $scope.autoDisplayFields = fieldsToSelect || [];

                if (name) {
                    angular.forEach($scope.selectedFields, function(selectedField) {
                        if (selectedField.basic.name === name) {
                            selected = true;
                        }
                    });

                    if (!selected && fieldsToSelect && $.inArray(name, fieldsToSelect) !== -1) {
                        selected = true;
                    }

                    if (selected) {
                        $timeout(function() {
                            $($(".multiselect-container").find(":checkbox")).each(function() {
                                if (field.label === $.trim($(this).parent().text()) && !this.checked) {
                                    $(this).click();
                                }
                            });
                        });
                    }
                }
            });
        };

        $scope.initHistoryGrid = function() {
            $timeout(function() {
                $scope.updateInstanceGridFields($scope.modificationFields);
            });
        };

        $scope.printDateTime = function(dt) {
            return moment(dt, 'YYYY-MM-DD HH:mm ZZ').format('YYYY-MM-DD, HH:mm');
        };
    });

    /**
    * The FilterCtrl controller is used on the 'Data Browser' view for the right panel.
    */
    controllers.controller('FilterCtrl', function ($rootScope, $scope) {

    });

    /**
    * The SettingsCtrl controller is used on the 'Settings' view.
    */
    controllers.controller('SettingsCtrl', function ($scope, Entities, MdsSettings) {
        innerLayout({
            spacing_closed: 30,
            east__minSize: 200,
            east__maxSize: 350
        });
        workInProgress.setActualEntity(Entities, undefined);

        var result = [];
        $scope.settings = MdsSettings.getSettings();
        $scope.entities = Entities.query();

        $scope.timeUnits = [
            { value: 'HOURS', label: $scope.msg('mds.dateTimeUnits.hours') },
            { value: 'DAYS', label: $scope.msg('mds.dateTimeUnits.days') },
            { value: 'WEEKS', label: $scope.msg('mds.dateTimeUnits.weeks') },
            { value: 'MONTHS', label: $scope.msg('mds.dateTimeUnits.months') },
            { value: 'YEARS', label: $scope.msg('mds.dateTimeUnits.years') }
        ];

        /**
        * This function is used to get entity metadata from controller and convert it for further usage
        */
        $scope.getEntities = function () {
            if (result.length === 0) {
                angular.forEach($scope.entities, function (entity) {
                    var module = entity.module === null ? "MDS" : entity.module.replace(/ /g, ''),
                        found = false;
                    angular.forEach(result, function (mod) {
                        if (module === mod.name) {
                            mod.entities.push(entity.name + (entity.namespace !== null ? " (" + entity.namespace + ")" : ""));
                            found = true;
                        }
                    });
                    if (!found) {
                        result.push({name: module, entities: [entity.name + (entity.namespace !== null ? " (" + entity.namespace + ")" : "")]});
                    }
                });
            }
            return result;
        };

        /**
        * This function checking if input and select fields for time selection should be disabled.
        * They are only enabled when deleting is set to "trash" and checkbox is selected
        */
        $scope.checkTimeSelectDisable = function () {
            return $scope.settings.deleteMode !== "TRASH" || !$scope.settings.emptyTrash;
        };

        /**
        * This function checking if checkbox in UI should be disabled. It is should be enabled when deleting is set to "trash"
        */
        $scope.checkCheckboxDisable = function () {
            return $scope.settings.deleteMode !== "TRASH";
        };

        /**
        * This function it is called when we change the radio. It's used for dynamically changing availability to fields
        */
        $scope.checkDisable = function () {
            $scope.checkTimeSelectDisable();
            $scope.checkCheckboxDisable();
        };

        /**
        * Get imported file and sends it to controller.
        */
        $scope.importFile = function () {
            MdsSettings.importFile($("#importFile")[0].files[0]);
        };

        /**
        * Sending information what entities we want to export to controller
        */
        $scope.exportData = function () {
            MdsSettings.exportData($("table")[0]);
        };

        /**
        * Sends new settings to controller
        */
        $scope.saveSettings = function () {
            MdsSettings.saveSettings({}, $scope.settings,
                function () {
                    handleResponse('mds.success', 'mds.dataRetention.success', '');
                }, function (response) {
                    handleResponse('mds.error', 'mds.dataRetention.error', response);
                });
        };

        /**
        * Checks all schema checkboxes and setting the general schema checkbox state
        */
        $scope.updateAllSchemasCheckbox = function () {
            if ($('input[id^="schema"]:checked').length >= $('input[id^="schema"]').length - 1) {
                $("#schema-all").prop("checked", true);
                $("#schema-all").prop("indeterminate", false);
            } else if ($('input[id^="schema"]:checked').length === 0) {
                $("#schema-all").prop("checked", false);
                $("#schema-all").prop("indeterminate", false);
            } else {
                $("#schema-all").prop("checked", false);
                $("#schema-all").prop("indeterminate", true);
            }
        };

        /**
        * Checks all data checkboxes and setting the general data checkbox state
        */
        $scope.updateAllDataCheckbox = function () {
            if ($('input[id^="data"]:checked').length >= $('input[id^="data"]').length - 1) {
                $("#data-all").prop("checked", true);
                $("#data-all").prop("indeterminate", false);
            } else if ($('input[id^="data"]:checked').length === 0) {
                $("#data-all").prop("checked", false);
                $("#data-all").prop("indeterminate", false);
            } else {
                $("#data-all").prop("checked", false);
                $("#data-all").prop("indeterminate", true);
            }
        };

        /**
        * Checks all schema checkboxes in the module and setting the module schema checkbox state
        */
        $scope.updateModuleSchemaCheckbox = function (module) {
            if ($('input[id^="schema-' + module + '-"]:checked').length === $('input[id^="schema-' + module + '-"]').length) {
                $("#schema-" + module).prop("checked", true);
                $("#schema-" + module).prop("indeterminate", false);
            } else if ($('input[id^="schema-' + module + '-"]:checked').length === 0) {
                $("#schema-" + module).prop("checked", false);
                $("#schema-" + module).prop("indeterminate", false);
            } else {
                $("#schema-" + module).prop("checked", false);
                $("#schema-" + module).prop("indeterminate", true);
            }
        };

        /**
        * Checks all data checkboxes in the module and setting the module data checkbox state
        */
        $scope.updateModuleDataCheckbox = function (module) {
            if ($('input[id^="data-' + module + '-"]:checked').length === $('input[id^="data-' + module + '-"]').length) {
                $("#data-" + module).prop("checked", true);
                $("#data-" + module).prop("indeterminate", false);
            } else if ($('input[id^="data-' + module + '-"]:checked').length === 0) {
                $("#data-" + module).prop("checked", false);
                $("#data-" + module).prop("indeterminate", false);
            } else {
                $("#data-" + module).prop("checked", false);
                $("#data-" + module).prop("indeterminate", true);
            }
        };

        /**
        * Called when we change state of "Schema" checkbox on the top of the table
        * When checked: select all schema checkboxes in the table
        * When unchecked: deselect all schema and data checkboxes
        */
        $scope.checkAllSchemas = function () {
            $('input[id^="schema"]').prop("indeterminate", false);
            if ($("#schema-all")[0].checked) {
                $('input[id^="schema"]').prop("checked", true);
            } else {
                $('input[id^="data"]').prop("indeterminate", false);
                $('input[id^="schema"]').prop("checked", false);
                $('input[id^="data"]').prop("checked", false);
            }
        };

        /**
        * Called when we change state of "Data" checkbox on the top of the table
        * When checked: select all schema and data checkboxes in the table
        * When unchecked: deselect all data checkboxes
        */
        $scope.checkAllData = function () {
            $('input[id^="schema"]').prop("indeterminate", false);
            if ($("#data-all")[0].checked) {
                $('input[id^="data"]').prop("indeterminate", false);
                $('input[id^="data"]').prop("checked", true);
                $('input[id^="schema"]').prop("checked", true);
            } else {
                $('input[id^="data"]').prop("checked", false);
            }
        };

        /**
         * Called when we change state of "Schema" checkbox on the module header
         * When checked: select all schema checkboxes in the module section
         * When unchecked: deselect all schema and data checkboxes in the module section
         */
        $scope.checkModuleSchemas = function (id) {
            if ($("#schema-" + id.name)[0].checked) {
                $('input[id^="schema-' + id.name + '"]').prop("checked", true);
            } else {
                $('input[id^="schema-' + id.name + '"]').prop("checked", false);
                $('input[id^="data-' + id.name + '"]').prop("checked", false);
            }

            $scope.updateModuleSchemaCheckbox(id.name);
            $scope.updateModuleDataCheckbox(id.name);

            $scope.updateAllSchemasCheckbox();
            $scope.updateAllDataCheckbox();
        };

        /**
        * Called when we change state of "Data" checkbox on the module header
        * When checked: select all schema and data checkboxes in the module section
        * When unchecked: deselect all data checkboxes in the module section
        */
        $scope.checkModuleData = function (id) {
            if ($("#data-" + id.name)[0].checked) {
                $('input[id^="data-' + id.name + '"]').prop("checked", true);
                $('input[id^="schema-' + id.name + '"]').prop("checked", true);
            } else {
                $('input[id^="data-' + id.name + '"]').prop("checked", false);
            }

            $scope.updateModuleSchemaCheckbox(id.name);
            $scope.updateModuleDataCheckbox(id.name);

            $scope.updateAllSchemasCheckbox();
            $scope.updateAllDataCheckbox();
        };

        /**
        * Called when we change state of "Data" checkbox in the entity row
        * When checked: select schema checkbox in the entity row
        */
        $scope.checkSchema = function (id, entity) {
            var name = id.name + '-' + entity;
            if ($('input[id^="data-' + name + '"]')[0].checked) {
                $('input[id^="schema-' + name + '"]').prop("checked", true);
            }

            $scope.updateModuleSchemaCheckbox(id.name);
            $scope.updateModuleDataCheckbox(id.name);

            $scope.updateAllSchemasCheckbox();
            $scope.updateAllDataCheckbox();
        };

        /**
        * Called when we change state of "Schema" checkbox in the entity row
        * When unchecked: deselect data checkbox in the entity row
        */
        $scope.uncheckData = function (id, entity) {
            var name = id.name + '-' + entity;
            if (!$('input[id^="schema-' + name + '"]')[0].checked) {
                $('input[id^="data-' + name + '"]').prop("checked", false);
            }

            $scope.updateModuleSchemaCheckbox(id.name);
            $scope.updateModuleDataCheckbox(id.name);

            $scope.updateAllSchemasCheckbox();
            $scope.updateAllDataCheckbox();
        };

        /**
        * Hiding and collapsing module entities and changing arrow icon
        * after clicking on arrow next to module name.
        */
        $scope.hideModule = function (id) {
            if ($("." + id.name + ":hidden").length > 0) {
                $("." + id.name).show("slow");
                $("#" + id.name + "-arrow").addClass("icon-caret-down");
                $("#" + id.name + "-arrow").removeClass("icon-caret-right");
            } else {
                $("." + id.name).hide("slow");
                $("#" + id.name + "-arrow").addClass("icon-caret-right");
                $("#" + id.name + "-arrow").removeClass("icon-caret-down");
            }
        };
    });
}());
