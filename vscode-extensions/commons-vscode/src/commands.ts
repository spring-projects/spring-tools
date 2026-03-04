'use strict';

import {
    ExtensionContext,
    Position,
    Selection,
    Uri,
    commands,
    window
} from 'vscode';
import { Position as LspPosition } from 'vscode-languageclient';



export function registerCommands(context: ExtensionContext) {
    commands.getCommands(false).then(commands => {
        if (!commandExists(commands, "sts.open.url")) {
            registerOpenUrl(context, "sts.open.url");
        }
    
        if (!commandExists(commands, "sts.showHoverAtPosition")) {
            registerShowHoverAtPosition(context, "sts.showHoverAtPosition");
        }
    });
}

function registerOpenUrl(context: ExtensionContext, commandId: string) {
    context.subscriptions.push(commands.registerCommand(commandId, (url) => {
        commands.executeCommand('vscode.open', Uri.parse(url))
    }));
}

function registerShowHoverAtPosition(context: ExtensionContext, commandId: string) {
    commands.registerCommand(commandId, (position: LspPosition) => {
        const editor = window.activeTextEditor;
        const vsPosition = new Position(position.line, position.character);
        editor.selection = new Selection(vsPosition, vsPosition);
        commands.executeCommand('editor.action.showHover');
    });
}

function commandExists(commands: string[], commandId: string) {
    return commands.indexOf(commandId) >= 0;
}