define(['react', 'util/formatters'], function(React, F) {
    'use strict';

    const BrowserDirectoryItem = React.createClass({

        render() {
            const { name, type, size, selected } = this.props;
            return (
                <li tabIndex="-1"
                    className={type + (selected ? ' active' : '')}
                    onKeyUp={this.onKeyUp}
                    onClick={this.onClick}>
                    <span className="name" title={name}>{name}</span>
                    {type === 'file' ? (
                        <span className="size">{F.bytes.pretty(size)}</span>
                    ) : null}
                </li>
            );
        },

        onKeyUp(e) {
            // TODO: handle enter
        },

        onClick() {
            const { name, type } = this.props;
            if (type === 'file') this.props.onSelectItem(name);
            else this.props.onOpenDirectory(name)
        }

    });

    return BrowserDirectoryItem;
});
