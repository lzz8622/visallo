define([
    'data/web-worker/store/actions',
    'data/web-worker/util/ajax'
], function(actions, ajax) {

    actions.protectFromMain();

    const key = 'ingest-cloud-s3';
    const api = {
        connect: (credentials) => (dispatch, getState) => {
            dispatch({
                type: 'INGEST_CLOUD_S3_SET_CREDENTIALS',
                payload: credentials
            })
            dispatch(api.openDirectory())
        },

        openDirectory: (name) => (dispatch, getState) => {
            const { credentials, cwd, contentsByDir } = getState()[key]
            const { accessKey, secret } = credentials;

            if (accessKey && secret) {
                var newDir = cwd;
                if (name === '..') {
                    if (cwd.length) {
                        newDir = cwd.slice(0, cwd.length - 1);
                    }
                } else {
                    newDir = _.compact(cwd.concat([name]));
                }
                const newDirStr = newDir.join('/')

                dispatch({
                    type: 'INGEST_CLOUD_S3_CD',
                    payload: newDir
                })

                if (!contentsByDir[newDirStr]) {
                    ajax('POST', '/org/visallo/web/ingest/cloud/s3', { accessKey, secret, path: newDirStr })
                        .then(response => {
                            const { items, errorMessage } = response;
                            dispatch({
                                type: 'INGEST_CLOUD_S3_LOAD_CONTENTS',
                                payload: { path: newDirStr, contents: items, errorMessage }
                            })
                        })
                        .catch(response => {
                            console.error(response)
                            dispatch({ type: 'INGEST_CLOUD_S3_LOAD_CONTENTS' })
                        })
                }
            }
        },

        selectItem: (name) => ({
            type: 'INGEST_CLOUD_S3_SELECT',
            payload: name
        })

        //importSelected: () => (dispatch, getState) => {
            //const { accessKey, secret, cwd, contentsByDir } = getState()[key]
            //const path = cwd.join('/')

            //Promise.resolve()
                //.then(() => {
                    //if (contentsByDir[path]) {
                        //return contentsByDir[path]
                    //} else {
                        //return ajax('POST', '/org/visallo/web/ingest/cloud/s3', { accessKey, secret, path })
                            //.then(response => {
                                //const { items, errorMessage } = response;
                                //dispatch({
                                    //type: 'INGEST_CLOUD_S3_LOAD_CONTENTS',
                                    //payload: { path: newDirStr, contents: items, errorMessage }
                                //})
                                //return items;
                            //})
                    //}
                //})
                //.then(items => {
                    //items.filter(i => i.type === 'file')
                    //console.log(items)
                //})
        //}
    };

    return api;
})

