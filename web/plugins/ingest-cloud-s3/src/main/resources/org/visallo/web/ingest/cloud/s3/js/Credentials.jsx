define([
    'react',
    './i18n'
], function(React, i18n) {

    const { PropTypes } = React;
    const Credentials = React.createClass({
        propTypes: {
            accessKey: PropTypes.string,
            secret: PropTypes.string,
            errorMessage: PropTypes.string,
            onChangeCredentials: PropTypes.func.isRequired,
            onOpenDirectory: PropTypes.func.isRequired
        },

        componentDidMount() {
            // FIXME: remove, just for faster dev testing
            if (localStorage.aws) {
                const { u:accessKey, p:secret } = JSON.parse(localStorage.aws);
                this.props.onChangeCredentials({ accessKey, secret })
            }
        },

        render() {
            const { accessKey, secret, errorMessage } = this.props;
            return (
                <div>
                    <div className="help">
                        <a target="_blank" href={i18n('help_url')}>{i18n('help')}</a>
                    </div>

                    {errorMessage ? (
                        <div className="alert alert-error">{errorMessage}</div>
                    ) : null}

                    <label>
                        {i18n('access_key')}
                        <input type="text" value={accessKey} onChange={this.handleChange('accessKey')} onKeyDown={this.onKeyDown} />
                    </label>
                    <label>
                        {i18n('secret')}
                        <input type="password" value={secret} onKeyDown={this.onKeyDown} onChange={this.handleChange('secret')} />
                    </label>

                    <div className="buttons">
                    <button onClick={this.connect} className="btn btn-primary">{i18n('connect')}</button>
                    </div>
                </div>
            );
        },

        // FIXME: can't delete last letter
        onKeyDown(event) {
            event.stopPropagation();
        },

        handleChange(stateField) {
            return event => {
                this.props.onChangeCredentials({ [stateField]: event.target.value })
            }
        },

        connect(event) {
            this.props.onOpenDirectory();
        }
    });

    return Credentials;
});

