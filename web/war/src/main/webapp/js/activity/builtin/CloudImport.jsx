define([
    'react'
], function(React) {
    'use strict';

    const PropTypes = React.PropTypes;
    const CloudImport = React.createClass({
        propTypes: {
        },
        render() {
            console.log(this.props)
            return (
                <button className="btn btn-mini">Open</button>
            )
        }
    });

    return CloudImport;
});
