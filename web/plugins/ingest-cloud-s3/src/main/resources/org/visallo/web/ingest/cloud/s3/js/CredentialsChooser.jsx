define([
    'react',
    './i18n',
    'components/RegistryInjectorHOC',
    'components/Attacher'
], function(React, i18n, RegistryInjectorHOC, Attacher) {
    'use strict';

    const DEFAULT_AUTH_ID = 'basic_auth'
    const AUTH_EXTENSION_POINT = 'org.visallo.ingest.cloud.s3.auth';
    RegistryInjectorHOC.registry.documentExtensionPoint(
        AUTH_EXTENSION_POINT,
        'Provide credential managers to S3 ingest',
        function(e) {
            return _.every(
                ['id', 'componentPath', 'displayName', 'providerClass'],
                p => _.isString(e[p]) && e[p]
            );
        }
    );
    RegistryInjectorHOC.registry.registerExtension(AUTH_EXTENSION_POINT, {
        id: 'basic_auth',
        displayName: i18n('basic_auth'),
        componentPath: 'org/visallo/web/ingest/cloud/s3/dist/BasicAuth',
        providerClass: 'org.visallo.web.ingest.cloud.s3.BasicAuthProvider'
    });

    const PropTypes = React.PropTypes;
    const CredentialsChooser = React.createClass({

        propTypes: {
            authenticationId: PropTypes.string,
            onConnect: PropTypes.func.isRequired,
            errorMessage: PropTypes.string,
            loading: PropTypes.bool
        },

        getDefaultProps() {
            return { authenticationId: DEFAULT_AUTH_ID }
        },

        getInitialState() {
            return { componentPath: '' }
        },

        render() {
            const { registry } = this.props;
            const types = _.sortBy(registry[AUTH_EXTENSION_POINT], r => r.displayName.toLowerCase());
            const componentPath = this.getComponentPath(types);
            const behavior = {
                onConnect: (attacher, credentials) => this.props.onConnect(credentials)
            }

            return (
                <div className="import-s3-credentials">
                    <select defaultValue={DEFAULT_AUTH_ID} onChange={this.onChange}>
                        {types.length === 1 ? null : (
                            <option value=''>{i18n(types.length ? 'credentials' : 'nocredentials')}</option>
                        )}
                        {types.map(r => (<option key={r.id} value={r.componentPath}>{r.displayName}</option>)) }
                    </select>
                    {componentPath ? (<Attacher behavior={behavior} loading={loading} componentPath={componentPath} />) : null}
                </div>
            );
        },

        onChange(event) {
            const componentPath = event.target.value;
            this.setState({ componentPath })
        },

        getComponentPath(types) {
            const { componentPath } = this.state;
            if (!componentPath) {
                const selected = _.findWhere(types, t => t.id === DEFAULT_AUTH_ID);
                if (selected) return selected.componentPath;
                if (types.length) return types[0].componentPath;
            }
        }
    });

    return RegistryInjectorHOC(CredentialsChooser, [AUTH_EXTENSION_POINT]);
});
