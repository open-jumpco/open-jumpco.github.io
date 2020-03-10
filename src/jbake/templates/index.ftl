<#include "header.ftl">

<#include "menu.ftl">

<div class="page-header">
    <h1>Projects</h1>
</div>

<#list projects?sort_by("title") as project>
    <#if (project.status == "published")>
        <div class="panel panel-primary">
            <div class="panel-heading"><#escape x as x?xml>${project.title}</#escape></div>
            <div class="panel-body">
                <p>Version: ${project.version}</p>
                <p>${project.summary}</p>
                <p><a class="btn btn-primary" href="${project.uri}">Learn more</a></p>
            </div>
        </div>
    </#if>
</#list>


<div class="page-header">
    <h1>Blog</h1>
</div>
<#list posts as post>
    <#if (post.status == "published")>
        <a href="${post.uri}"><h1><#escape x as x?xml>${post.title}</#escape></h1></a>
        <p>${post.date?string("dd MMMM yyyy")}</p>
        <p>${post.summary}</p>
    </#if>
</#list>

<hr/>

<p>Older posts are available in the <a href="${content.rootpath}${config.archive_file}">archive</a>.</p>

<#include "footer.ftl">
