define([
    'react',
    './Browser',
    './Credentials'
], function(
    React,
    Browser,
    Credentials) {

    const S3Configuration = function(props) {
        const { authenticated } = props;

        return authenticated ?
            <Browser {...({onOpenDirectory, onSelectItem, onImport, contentsByDir, cwd} = props)} /> :
            <Credentials {...({onChangeCredentials, accessKey, secret, errorMessage} = props)} />
    };

    return S3Configuration;
});

