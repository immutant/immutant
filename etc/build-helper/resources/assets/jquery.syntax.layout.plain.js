// This file is part of the "jQuery.Syntax" project, and is distributed under the MIT License.
// Copyright (c) 2011 Samuel G. D. Williams. <http://www.oriontransfer.co.nz>


Syntax.layouts.plain=function(options,code,container){var toolbar=jQuery('<div class="toolbar">');var scrollContainer=jQuery('<div class="syntax plain highlighted">');code.removeClass('syntax');scrollContainer.append(code);return jQuery('<div class="syntax-container">').append(toolbar).append(scrollContainer);};